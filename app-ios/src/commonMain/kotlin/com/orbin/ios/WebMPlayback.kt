package com.orbin.ios

/** A playback command is acknowledged only once the page has a video element. */
internal data class WebMPlaybackRequest(
    val command: Int,
    val play: Boolean,
    val muted: Boolean,
    val loop: Boolean,
)

/**
 * Reconcile delayed WebKit documents without restarting a manually paused clip on every poll.
 * Mute and loop can change independently; an explicit play command can replay an ended clip.
 */
internal fun webMPlaybackScript(request: WebMPlaybackRequest): String {
    val command = request.command
    val play = request.play
    val muted = request.muted
    val loop = request.loop
    return """
        (function(){
            var v=document.getElementById('v');
            if(!v)return null;
            function state(){return (v.paused?'0':'1')+','+v.currentTime+','+v.duration+','+(v.muted?'1':'0');}
            if(window.__orbinPlaybackCommand!==undefined&&window.__orbinPlaybackCommand>$command)return state();
            v.muted=$muted;
            v.loop=$loop;
            if(window.__orbinPlaybackCommand!==$command){
                window.__orbinPlaybackCommand=$command;
                if($play){
                    if(v.ended)v.currentTime=0;
                    var pending=v.play();
                    if(pending&&pending.catch)pending.catch(function(){});
                }else{
                    v.pause();
                }
            }
            return state();
        })()
        """.trimIndent()
}
