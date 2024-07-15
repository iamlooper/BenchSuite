LOCAL_PATH := $(call my-dir)/src

include $(CLEAR_VARS)
LOCAL_MODULE := hackbench
LOCAL_SRC_FILES := hackbench.c
include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)
LOCAL_MODULE := pipebench
LOCAL_SRC_FILES := pipebench.c
include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)
LOCAL_MODULE := callbench
LOCAL_SRC_FILES := callbench.c
include $(BUILD_EXECUTABLE)