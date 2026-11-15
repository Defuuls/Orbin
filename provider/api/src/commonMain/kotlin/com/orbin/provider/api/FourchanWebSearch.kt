package com.orbin.provider.api

import com.orbin.core.model.PostId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Browser-produced text results; remote HTML is never rendered as trusted app content. */
data class WebSearchHit(
    val link: ThreadLink,
    val subject: String,
    val comment: String,
    val name: String,
    val tripcode: String = "",
    val countryCode: String = "",
    val countryName: String = "",
)

data class FourchanSearchPage(
    val hits: List<WebSearchHit>,
    val nextOffset: Int?,
    val verifying: Boolean = false,
)

/** Only an already configured canonical 4chan source offers this website search. */
fun fourchanWebSearchProvider(providers: List<ProviderMetadata>): ProviderMetadata? =
    providers.singleOrNull {
        UriParts.parse(it.baseUrl)?.host?.lowercase() in
            setOf("boards.4chan.org", "boards.4channel.org")
    }

fun fourchanWebSearchUrl(
    query: String,
    offset: Int = 0,
): String? {
    val text = query.trim().takeIf { it.isNotEmpty() } ?: return null
    if (offset < 0) return null
    val encoded =
        buildString {
            text.encodeToByteArray().forEach { byte ->
                val n = byte.toInt() and URL_QUERY_UNSIGNED_BYTE_MASK
                if (isUnreservedQueryByte(n)) {
                    append(n.toChar())
                } else {
                    append('%').append(n.toString(URL_QUERY_HEX_RADIX).uppercase().padStart(URL_QUERY_HEX_DIGITS, '0'))
                }
            }
        }
    return "https://find.4chan.org/?q=$encoded&o=$offset"
}

/** Navigation is restricted to the real search site; verification frames are handled by WebKit. */
fun isFourchanWebSearchUrl(url: String): Boolean {
    val parts = UriParts.parse(url) ?: return false
    val authority =
        url
            .substringAfter("://", "")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
    return parts.scheme.equals("https", true) &&
        parts.host.equals("find.4chan.org", true) &&
        '@' !in authority &&
        ':' !in authority
}

/** A failed/changed page is an error, not an invented empty result set. */
fun parseFourchanWebSearchPage(
    payload: String,
    provider: ProviderMetadata,
): FourchanSearchPage? =
    runCatching {
        var parsed = Json.parseToJsonElement(payload)
        if (parsed is JsonPrimitive && parsed.isString) parsed = Json.parseToJsonElement(parsed.content)
        val obj = parsed as? JsonObject ?: return null
        if (obj["challenge"]?.jsonPrimitive?.content ==
            "true"
        ) {
            return FourchanSearchPage(emptyList(), null, verifying = true)
        }
        if (obj["recognized"]?.jsonPrimitive?.content != "true") return null
        val hits =
            (obj["hits"] as? JsonArray)
                .orEmpty()
                .mapNotNull { value ->
                    val hit = value.jsonObject

                    fun field(key: String) = hit[key]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val link = parseThreadLink(field("url"), listOf(provider)) ?: return@mapNotNull null
                    val post = field("post").toLongOrNull()?.takeIf { it > 0 }?.let(::PostId)
                    WebSearchHit(
                        link.copy(post = post ?: link.post),
                        field("subject"),
                        field("comment"),
                        field("name"),
                        field("tripcode"),
                        field("countryCode"),
                        field("countryName"),
                    )
                }.distinctBy { it.link }
        FourchanSearchPage(hits, obj["next"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 })
    }.getOrNull()

/**
 * Read-only DOM inspection, evaluated by native code after page load. No JavaScript bridge or
 * challenge bypass is installed. Only Cloudflare verification is shown as a web page.
 */
fun fourchanSearchInspectionScript(offset: Int): String =
    """
    (function(){
      var out=[], containers=document.querySelectorAll('.postContainer');
      for(var i=0;i<containers.length;i++){
        var c=containers[i], p=c.querySelector('.post'), m=c.querySelector('.postMessage');
        var id=(c.id||'').match(/^(?:pc|p|m)([0-9]+)$/)||(p&&p.id||'').match(/^(?:pc|p|m)([0-9]+)$/);
        var links=c.querySelectorAll('a[href]'), chosen=null;
        for(var j=0;j<links.length;j++){
          var a=links[j]; if(a.closest('.postMessage'))continue;
          if(!/^https:\/\/boards\.4chan(?:nel)?\.org\/[A-Za-z0-9_-]+\/thread\/[0-9]+/.test(a.href))continue;
          if(!chosen)chosen=a.href;
          if(id&&a.hash==='#p'+id[1]){chosen=a.href;break;}
        }
        if(!chosen)continue;
        function text(s){var e=c.querySelector(s);return e?(e.textContent||'').trim():'';}
        var flag=c.querySelector('.countryFlag'), cc=flag&&(flag.className||'').match(/flag-([a-z]{2})/i);
        out.push({url:chosen,post:id?id[1]:'',subject:text('.subject'),comment:m?(m.innerText||m.textContent||''):'',name:text('.name'),tripcode:text('.postertrip'),countryCode:cc?cc[1].toUpperCase():'',countryName:flag?(flag.title||''):''});
      }
      var recognized=out.length>0||!!document.querySelector('input[name="q"]');
      var html=document.documentElement.outerHTML.toLowerCase();
      var challenge=!recognized&&(html.indexOf('cf-chl')>=0||html.indexOf('challenge-platform')>=0||document.title.toLowerCase().indexOf('just a moment')>=0);
      var next=null, all=document.querySelectorAll('a[href]');
      for(var k=0;k<all.length;k++){
        try{var u=new URL(all[k].href);if(u.host!=='find.4chan.org')continue;
          var n=parseInt(u.searchParams.get('o'));if(n>$offset&&(next===null||n<next))next=n;
        }catch(e){}
      }
      return JSON.stringify({recognized:recognized,challenge:challenge,hits:out,next:next});
    })()
    """.trimIndent()

/** RFC 3986 unreserved ASCII only: high UTF-8 bytes must remain percent escaped. */
private fun isUnreservedQueryByte(value: Int): Boolean {
    val character = value.toChar()
    val letter = character in 'A'..'Z' || character in 'a'..'z'
    val digit = character in '0'..'9'
    return letter || digit || character in "-._~"
}

private const val URL_QUERY_UNSIGNED_BYTE_MASK = 0xFF
private const val URL_QUERY_HEX_RADIX = 16
private const val URL_QUERY_HEX_DIGITS = 2
