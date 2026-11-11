package hu.szintai.hakiosk.ui.dashboard

import org.json.JSONObject

/**
 * JavaScript injected into the Home Assistant frontend before its own scripts run.
 */
object KioskScripts {

    /**
     * Logs the WebView in with a long-lived token, hides the sidebar and (once the
     * Lovelace root has rendered) the top toolbar, so only the dashboard is visible.
     */
    fun bootstrap(token: String): String {
        val quotedToken = JSONObject.quote(token)
        return """
            (function () {
                try {
                    var origin = location.origin;
                    localStorage.setItem('hassTokens', JSON.stringify({
                        access_token: $quotedToken,
                        token_type: 'Bearer',
                        expires_in: 315360000,
                        expires: Date.now() + 315360000000,
                        hassUrl: origin,
                        clientId: origin + '/',
                        refresh_token: ''
                    }));
                    localStorage.setItem('dockedSidebar', '"always_hidden"');
                } catch (e) {}

                function sr(el) { return el && el.shadowRoot; }
                function addStyle(root, css) {
                    if (!root || root.querySelector('#kiosk-style')) return;
                    var style = document.createElement('style');
                    style.id = 'kiosk-style';
                    style.textContent = css;
                    root.appendChild(style);
                }
                function deepFind(root, tag) {
                    var all = root.querySelectorAll('*');
                    for (var i = 0; i < all.length; i++) {
                        if (all[i].localName === tag) return all[i];
                        if (all[i].shadowRoot) {
                            var f = deepFind(all[i].shadowRoot, tag);
                            if (f) return f;
                        }
                    }
                    return null;
                }
                function hideChrome() {
                    try {
                        var ha = document.querySelector('home-assistant');
                        var main = sr(ha).querySelector('home-assistant-main');
                        var panel = sr(main).querySelector('ha-panel-lovelace');
                        var root = sr(sr(panel).querySelector('hui-root'));
                        if (!root) return;
                        addStyle(root,
                            '.header, .toolbar { display: none !important; }' +
                            ':host { --header-height: 0px !important; }' +
                            '#view { padding-top: 0 !important; }');
                        var view = deepFind(root, 'hui-sections-view');
                        if (view) addStyle(sr(view), '.container { padding: 10px 12px !important; }');
                    } catch (e) {}
                }
                setInterval(hideChrome, 500);
            })();
        """.trimIndent()
    }
}
