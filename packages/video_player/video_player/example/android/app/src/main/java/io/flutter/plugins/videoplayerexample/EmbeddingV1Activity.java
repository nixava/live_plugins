// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.videoplayerexample;

import android.os.Bundle;
import android.view.WindowManager;

import io.flutter.plugins.videoplayer.VideoPlayerPlugin;

@SuppressWarnings("deprecation")
public class EmbeddingV1Activity extends io.flutter.app.FlutterActivity {
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    VideoPlayerPlugin.registerWith(
        registrarFor("io.flutter.plugins.videoplayer.VideoPlayerPlugin"));
  }


}
