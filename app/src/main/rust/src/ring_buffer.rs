use std::sync::atomic::{AtomicU64, Ordering};

/// SPSC ring buffer writer shared between Rust (producer) and Kotlin (consumer).
///
/// Layout:
///   0x0000  8B  magic (0x42454E4348535549 = "BENCHSUI")
///   0x0008  4B  version
///   0x000C  4B  capacity  (ring data capacity in bytes)
///   0x0010  4B  record_size (64)
///   0x0014  4B  _pad0
///   0x0018  8B  write_index (atomic)
///   0x0020  8B  read_index  (atomic, Kotlin writes)
///   0x0028  4B  state       (0=idle, 1=running, 2=done, 3=error)
///   0x002C  4B  dropped_count
///   0x0030  16B _pad1
///   0x0040  … ring data region
///
/// Safety invariants:
///   - `addr` points to a live `DirectByteBuffer` whose lifetime is managed by the JVM.
///   - Only one thread (Rust benchmark runner) writes records (SPSC).
///   - Kotlin reads records from any thread; we use `Ordering::Release` stores on
///     write_index so Kotlin's Acquire load sees the fully-written record.
pub struct RingBufferWriter {
    addr:     *mut u8,
    capacity: usize,   // ring data capacity (total buffer - HEADER_SIZE)
}

// Safety: RingBufferWriter is only constructed once and moved into Arc; all writes are
// serialised by the single producer thread in BridgeContext.
unsafe impl Send for RingBufferWriter {}
unsafe impl Sync for RingBufferWriter {}

const MAGIC:           u64 = 0x42454E4348535549;
const VERSION:         u32 = 1;
const RECORD_SIZE:     usize = 64;
const HEADER_SIZE:     usize = 0x0040;

// Header field byte offsets
const OFF_MAGIC:        usize = 0x0000;
const OFF_VERSION:      usize = 0x0008;
const OFF_CAPACITY:     usize = 0x000C;
const OFF_RECORD_SIZE:  usize = 0x0010;
const OFF_WRITE_INDEX:  usize = 0x0018;
const OFF_READ_INDEX:   usize = 0x0020;
const OFF_STATE:        usize = 0x0028;
const OFF_DROPPED:      usize = 0x002C;

// State constants
pub(crate) const STATE_IDLE:    u32 = 0;
pub(crate) const STATE_RUNNING: u32 = 1;
pub(crate) const STATE_DONE:    u32 = 2;
pub(crate) const STATE_ERROR:   u32 = 3;

// Record type constants
pub(crate) const RECORD_PROGRESS: u8 = 0x01;
pub(crate) const RECORD_SAMPLE:   u8 = 0x02;
#[allow(dead_code)]
pub(crate) const RECORD_LOG:      u8 = 0x03;
pub(crate) const RECORD_COMPLETE: u8 = 0xFF;

impl RingBufferWriter {
    /// Constructs a writer over an existing `DirectByteBuffer` memory region.
    /// `addr` must not include the header - the full buffer starts there.
    /// `total_capacity` is the byte length of the entire buffer (header + ring data).
    pub fn new(addr: *mut u8, total_capacity: usize) -> Self {
        assert!(total_capacity > HEADER_SIZE, "ring buffer too small for header");
        Self {
            addr,
            capacity: total_capacity - HEADER_SIZE,
        }
    }

    /// Writes the ring buffer header. Must be called exactly once after construction.
    pub fn write_header(&self) {
        unsafe {
            // magic (8B LE)
            self.write_u64(OFF_MAGIC, MAGIC);
            // version (4B LE)
            self.write_u32(OFF_VERSION, VERSION);
            // capacity = ring data capacity in bytes
            self.write_u32(OFF_CAPACITY, self.capacity as u32);
            // record_size (4B)
            self.write_u32(OFF_RECORD_SIZE, RECORD_SIZE as u32);
            // write_index = 0
            self.write_u64_atomic(OFF_WRITE_INDEX, 0);
            // read_index = 0
            self.write_u64_atomic(OFF_READ_INDEX, 0);
            // state = idle
            self.write_u32(OFF_STATE, STATE_IDLE);
            // dropped_count = 0
            self.write_u32(OFF_DROPPED, 0);
        }
    }

    /// Sets the engine state byte (idle / running / done / error).
    pub fn set_state(&self, state: u32) {
        unsafe { self.write_u32(OFF_STATE, state) }
    }

    /// Writes a 64-byte record to the ring slot given by the current write_index.
    /// If the ring is full (read_index + slots == write_index), increments dropped_count.
    ///
    /// Record layout:
    ///   0x00  1B  record_type
    ///   0x01  1B  category_id
    ///   0x02  2B  bench_id   (u16 LE)
    ///   0x04  4B  flags
    ///   0x08  4B  sequence
    ///   0x0C  4B  phase
    ///   0x10  4B  current_iter
    ///   0x14  4B  total_iter
    ///   0x18  8B  timestamp_ns (monotonic)
    ///   0x20  8B  value_primary  (f64 LE)
    ///   0x28  8B  value_secondary (f64 LE)
    ///   0x30  4B  unit
    ///   0x34  4B  metric_id
    ///   0x38  8B  _reserved
    #[allow(clippy::too_many_arguments)]
    pub fn write_record(
        &self,
        record_type:    u8,
        category_id:    u8,
        bench_id:       u16,
        flags:          u32,
        sequence:       u32,
        phase:          u32,
        current_iter:   u32,
        total_iter:     u32,
        timestamp_ns:   u64,
        value_primary:  f64,
        value_secondary: f64,
        unit:           u32,
        metric_id:      u32,
    ) {
        let slots = self.capacity / RECORD_SIZE;
        let wi    = unsafe { self.read_u64_atomic(OFF_WRITE_INDEX) };
        let ri    = unsafe { self.read_u64_atomic(OFF_READ_INDEX) };

        if wi - ri >= slots as u64 {
            // Ring full - increment dropped count and skip
            unsafe {
                let dropped = self.read_u32(OFF_DROPPED);
                self.write_u32(OFF_DROPPED, dropped.saturating_add(1));
            }
            return;
        }

        let slot = (wi as usize) % slots;
        let base = HEADER_SIZE + slot * RECORD_SIZE;

        unsafe {
            let p = self.addr.add(base);
            p.add(0x00).write(record_type);
            p.add(0x01).write(category_id);
            (p.add(0x02) as *mut u16).write_unaligned(bench_id.to_le());
            (p.add(0x04) as *mut u32).write_unaligned(flags.to_le());
            (p.add(0x08) as *mut u32).write_unaligned(sequence.to_le());
            (p.add(0x0C) as *mut u32).write_unaligned(phase.to_le());
            (p.add(0x10) as *mut u32).write_unaligned(current_iter.to_le());
            (p.add(0x14) as *mut u32).write_unaligned(total_iter.to_le());
            (p.add(0x18) as *mut u64).write_unaligned(timestamp_ns.to_le());
            (p.add(0x20) as *mut u64).write_unaligned(value_primary.to_bits().to_le());
            (p.add(0x28) as *mut u64).write_unaligned(value_secondary.to_bits().to_le());
            (p.add(0x30) as *mut u32).write_unaligned(unit.to_le());
            (p.add(0x34) as *mut u32).write_unaligned(metric_id.to_le());
            // Reserved - written as zero
            (p.add(0x38) as *mut u64).write_unaligned(0u64);
        }

        // Release store - Kotlin Acquire load on write_index sees the full record.
        unsafe { self.write_u64_atomic_release(OFF_WRITE_INDEX, wi + 1) }
    }

    // --- Private accessor helpers ---

    unsafe fn read_u32(&self, offset: usize) -> u32 {
        unsafe { u32::from_le((self.addr.add(offset) as *const u32).read_unaligned()) }
    }
    unsafe fn write_u32(&self, offset: usize, val: u32) {
        unsafe { (self.addr.add(offset) as *mut u32).write_unaligned(val.to_le()) };
    }
    unsafe fn write_u64(&self, offset: usize, val: u64) {
        unsafe { (self.addr.add(offset) as *mut u64).write_unaligned(val.to_le()) };
    }
    unsafe fn write_u64_atomic(&self, offset: usize, val: u64) {
        // Plain store (initialisation path, no concurrent reader yet).
        unsafe { (self.addr.add(offset) as *mut u64).write_unaligned(val.to_le()) };
    }
    unsafe fn write_u64_atomic_release(&self, offset: usize, val: u64) {
        // Use an AtomicU64 overlay for the Release semantic.
        let atomic = unsafe { &*(self.addr.add(offset) as *const AtomicU64) };
        atomic.store(val, Ordering::Release);
    }
    unsafe fn read_u64_atomic(&self, offset: usize) -> u64 {
        let atomic = unsafe { &*(self.addr.add(offset) as *const AtomicU64) };
        atomic.load(Ordering::Acquire)
    }
}
