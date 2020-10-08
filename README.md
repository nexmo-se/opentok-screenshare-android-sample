# Opentok Screenshare Android Sample

## Setup
Before compiling and running the app, we need to setup the Opentok Configuration.

In the `MainActivity.kt` file, change the `apiKey`, `sessionId` and `token` to your own token.

## Using The App
When the application starts, it will attempt to connect to the Opentok session.

Once it is connected, you can click on the `Start Camera` or `Start Screenshare` button to start either the camera or the screenshare and publish to the opentok session.

When you are done with the session, click on the `Disconnect` button to disconnect and close the app.

## How it works
This screenshare uses the Android MediaProjection approach to obtain the screen content from the OS. The screen frames are then rotated/flipped and sent out to WebRTC using a Custom Video Device (`CustomVideoCapturer.kt`).

Note: MediaProjection API needs to be running as a foreground service (not activity). Therefore, the frame data are passed back to the activity via a Binder.


## Credits
Credits to the Customer Solutions Engineering team from the Vonage API Platform.