MY_PATH := $(call my-dir)
include $(call all-subdir-makefiles) # this changes my-dir...
include $(CLEAR_VARS)
LOCAL_PATH := $(MY_PATH)

PROJECT_ROOT := $(LOCAL_PATH)
APP_MAIN_ROOT := $(LOCAL_PATH)/..
include $(CLEAR_VARS)

LOCAL_MODULE := c_SDDRRadio
LOCAL_CFLAGS += -DLOG_W_ENABLED -DLOG_D_ENABLED -DLOG_P_ENABLED

LOCAL_SRC_FILES := $(COMMON_SOURCE_FILES)
LOCAL_SRC_FILES += $(EXECUTABLE_SOURCE_FILES)

LOCAL_C_INCLUDES += $(PROJECT_ROOT)/include
LOCAL_C_INCLUDES += $(PROJECT_ROOT)/include/jerasure
LOCAL_C_INCLUDES += $(PROJECT_ROOT)/include/openssl
LOCAL_C_INCLUDES += $(PROJECT_ROOT)/source/protobuf/src

LOCAL_LDLIBS += $(APP_MAIN_ROOT)/jniLibs/armeabi-v7a/libcrypto.so
LOCAL_LDLIBS += $(APP_MAIN_ROOT)/jniLibs/armeabi-v7a/libcutils.so
LOCAL_LDLIBS += $(APP_MAIN_ROOT)/jniLibs/armeabi-v7a/libprotobuf.a
LOCAL_LDLIBS += -lc -ldl -llog -landroid

LOCAL_SHARED_LIBRARIES := libprotobuf

include $(BUILD_SHARED_LIBRARY)
