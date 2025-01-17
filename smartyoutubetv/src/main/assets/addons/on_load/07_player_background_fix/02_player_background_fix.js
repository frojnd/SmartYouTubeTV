/**
 * Description:
 * On the WebView engine low resolution play icon appears before each clip.
 * Fix that by replacing it with the simple black screen.
 */

console.log("Scripts::Running script player_background_fix2.js");

function PlayerBackgroundFixAddon() {
    this.run = function() {
        ElementWrapper.addHandler(this);
    };

    this.onCreate = function(video) {
        if (video != null) {
            video.poster = "data:image/gif,AAAA"; // transparent image
        }
    };
}

// if (DeviceUtils.isWebView() && !DeviceUtils.isExo())
//     new PlayerBackgroundFixAddon().run();

new PlayerBackgroundFixAddon().run();