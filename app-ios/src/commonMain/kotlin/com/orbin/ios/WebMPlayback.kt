package com.orbin.ios

import com.orbin.uinext.mediaPlaybackSpeed

/** Play is acknowledged only after WebKit fulfills it; a rejected current request can be retried. */
internal data class WebMPlaybackRequest(
    val command: Int,
    val play: Boolean,
    val muted: Boolean,
    val loop: Boolean,
    val speed: Float = 1f,
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
    val speed = mediaPlaybackSpeed(request.speed)
    return """
        (function(){
            var v=document.getElementById('v');
            function state(){return (v.paused?'0':'1')+','+v.currentTime+','+v.duration+','+(v.muted?'1':'0');}
            var requested=window.__orbinPlaybackRequested;
            if(requested&&requested.command>$command)return v?state():null;
            window.__orbinPlaybackRequested={command:$command,play:$play};
            if(!v)return null;
            v.muted=$muted;
            v.loop=$loop;
            v.playbackRate=$speed;
            function applied(play){
                window.__orbinPlaybackApplied=$command;
                window.__orbinPlaybackAppliedPlay=play;
                window.__orbinPlaybackCommand=$command;
            }
            var alreadyApplied=window.__orbinPlaybackApplied===$command&&window.__orbinPlaybackAppliedPlay===$play;
            if(!$play){
                if(!alreadyApplied){
                    window.__orbinPlaybackPending=null;
                    v.pause();
                    applied(false);
                }
                return state();
            }
            if(alreadyApplied)return state();
            var pending=window.__orbinPlaybackPending;
            if(pending&&pending.command===$command)return state();
            var attempt={command:$command,token:(window.__orbinPlaybackAttempt||0)+1};
            window.__orbinPlaybackAttempt=attempt.token;
            window.__orbinPlaybackPending=attempt;
            function isCurrent(){
                var latest=window.__orbinPlaybackRequested;
                return window.__orbinPlaybackPending===attempt&&latest&&latest.command===$command&&latest.play;
            }
            function fulfilled(){
                if(isCurrent()){
                    window.__orbinPlaybackPending=null;
                    window.__orbinPlaybackLastRejection=null;
                    applied(true);
                }else if(window.__orbinPlaybackRequested&&!window.__orbinPlaybackRequested.play){
                    v.pause();
                }
            }
            function rejected(error){
                if(!isCurrent())return;
                window.__orbinPlaybackPending=null;
                window.__orbinPlaybackLastRejection=String(error&&error.name?error.name+': '+error.message:error);
            }
            try{
                if(v.ended)v.currentTime=0;
                var promise=v.play();
                if(promise&&promise.then)promise.then(fulfilled,rejected);else fulfilled();
            }catch(error){rejected(error);}
            return state();
        })()
        """.trimIndent()
}
