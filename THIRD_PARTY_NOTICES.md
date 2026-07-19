# Third-party notices

## SFace face-recognition model

`composeApp/src/androidMain/assets/sface.onnx` is the int8-quantized
`face_recognition_sface_2021dec` model from the OpenCV Zoo project, used
on-device (via ONNX Runtime Mobile) for local face clustering in the Photos
app's People view. No network calls are made; inference runs entirely on
the device.

- Source: https://github.com/opencv/opencv_zoo/tree/main/models/face_recognition_sface
- License: Apache License, Version 2.0
- Copyright: OpenCV Zoo contributors

Licensed under the Apache License, Version 2.0 (the "License"); you may not
use this file except in compliance with the License. You may obtain a copy
of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless
required by applicable law or agreed to in writing, the model is
distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.

## ONNX Runtime Mobile

`com.microsoft.onnxruntime:onnxruntime-android` is used to run the model
above on-device.

- Source: https://github.com/microsoft/onnxruntime
- License: MIT License
- Copyright: Microsoft Corporation
