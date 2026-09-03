Communications Firewall for Android Devices

V1

Block unknown (Read: Unsaved contacts) phone calls and SMS, while providing an allowlist for keywords contained in a SMS to allow 2FA or other services to go through (Steam, Google, Microsoft, etc)

Fully vibecoded because I was getting 100+ spam calls a day. Took around 45 minutes from Prompt design to build.

Using GoDot 4.6, JDK 17, Android Command-Line Tools 

SMS Blocking is a STUB, currently it only serves as a tracker

To enable full SMS Managing we'd actually need a full SMS UI and functionality, since Android only allows the default SMS app to manage SMS. In order to make NukeCall the default SMS app we'd need a full SMS Functionality suite. Deferred until I actually need it.
