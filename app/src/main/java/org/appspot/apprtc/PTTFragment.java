/*
 *  Copyright 2015 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.appspot.apprtc;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import org.webrtc.RendererCommon.ScalingType;

/**
 * Fragment for call control.
 */
public class PTTFragment extends Fragment {
    private TextView statusView;
    private ImageButton PTTButton;
    private OnPTTEvents pttEvents;
    private ScalingType scalingType;
    private boolean videoCallEnabled = true;

    /**
     * Call control interface for container activity.
     */
    public interface OnPTTEvents {
        void onPTTPushed();
        void onPTTRelease();
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View controlView = inflater.inflate(R.layout.fragment_ptt, container, false);

        // Create UI controls.
        statusView = controlView.findViewById(R.id.status_text_ptt);
        ImageButton PTTButton = controlView.findViewById(R.id.button_ptt);

        PTTButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_UP://松开事件发生后执行代码的区域
                        pttEvents.onPTTRelease();
                        break;
                    case MotionEvent.ACTION_DOWN://按住事件发生后执行代码的区域
                        pttEvents.onPTTPushed();
                        break;
                    default:
                        break;
                }
                return true;
            }
        });


        // Add buttons click events.
        PTTButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pttEvents.onPTTPushed();
            }
        });

        return controlView;
    }

    @Override
    public void onStart() {
        super.onStart();

        boolean captureSliderEnabled = false;
        Bundle args = getArguments();
        if (args != null) {
            String contactName = args.getString(VideoRoomActivity.EXTRA_ROOMID);
            statusView.setText(contactName);
        }
    }

    // TODO(sakal): Replace with onAttach(Context) once we only support API level 23+.
    @SuppressWarnings("deprecation")
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        pttEvents = (OnPTTEvents) activity;
    }
}

