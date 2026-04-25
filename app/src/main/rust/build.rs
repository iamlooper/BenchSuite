use std::env;
use std::path::PathBuf;

fn main() {
    // Provide a hermetic protoc binary so prost-build works without a system install.
    // protoc runs on the HOST (not the Android target), so the vendored Linux/macOS/Windows
    // binary from protoc-bin-vendored matches any CI runner or developer machine.
    let protoc = protoc_bin_vendored::protoc_bin_path()
        .expect("protoc-bin-vendored: failed to locate bundled protoc binary");
    // SAFETY: build.rs is single-threaded; set_var is safe here.
    unsafe { env::set_var("PROTOC", protoc) };

    // Locate the canonical .proto sources shared with the Android protobuf Gradle plugin.
    // build.rs lives at app/src/main/rust/build.rs; proto/ is at app/src/main/proto/.
    let proto_dir = PathBuf::from(env::var("CARGO_MANIFEST_DIR").unwrap())
        .parent()
        .unwrap()
        .join("proto");

    let protos = [
        proto_dir.join("bridge.proto"),
        proto_dir.join("bench_commands.proto"),
        proto_dir.join("config.proto"),
        proto_dir.join("results.proto"),
    ];

    prost_build::Config::new()
        // Place generated modules into separate files so cross-package type paths are clean.
        // bench_commands.proto imports config.proto + results.proto (different package),
        // so generated Rust types land in crate::benchsuite_proto::{SuiteConfig, ...}.
        .out_dir(env::var("OUT_DIR").unwrap())
        .compile_protos(&protos, &[proto_dir])
        .expect("prost-build failed; check proto/ directory path and syntax");

    // Re-run if any .proto changes
    for p in &protos {
        println!("cargo:rerun-if-changed={}", p.display());
    }
}
