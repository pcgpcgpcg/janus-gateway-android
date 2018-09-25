#!/bin/bash

WEBRTC_ROOT=$1

if [[ -z "${WEBRTC_ROOT// }" ]]; then
    echo "Please specify WEBRTC_ROOT!"
    exit 1
fi

# app
cp ${WEBRTC_ROOT}/src/examples/androidapp/third_party/autobanh/lib/autobanh.jar ./app/libs/
cp ${WEBRTC_ROOT}/src/examples/androidapp/AndroidManifest.xml ./app/src/main/AndroidManifest.xml
rm -rf ./app/src/main/java/
cp -rf ${WEBRTC_ROOT}/src/examples/androidapp/src/ ./app/src/main/java/
rm -rf ./app/src/main/res/
cp -rf ${WEBRTC_ROOT}/src/examples/androidapp/res/ ./app/src/main/res/

# base_java
rm -rf ./base_java/src/main/java/
cp -rf ${WEBRTC_ROOT}/src/rtc_base/java/src/ ./base_java/src/main/java/

# audio_device_java
rm -rf ./audio_device_java/src/main/java/
cp -rf ${WEBRTC_ROOT}/src/modules/audio_device/android/java/src/ ./audio_device_java/src/main/java/

# libjingle_peerconnection
rm -rf ./libjingle_peerconnection/src/main/java/
cp -rf ${WEBRTC_ROOT}/src/sdk/android/api/ ./libjingle_peerconnection/src/main/java/
cp -rf ${WEBRTC_ROOT}/src/sdk/android/src/java/org/webrtc/* ./libjingle_peerconnection/src/main/java/org/webrtc/
