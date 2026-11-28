"""Browser tests for Pulse, run in headless Chrome against the real source modules
(via a local static server) and against the built single-file artifact.

  python3 tests/browser/run.py            core + a11y suites (blocking)
  python3 tests/browser/run.py --perf     also runs the perf suite (non-blocking, generous limits)
"""
import functools, http.server, socketserver, sys, threading, time, traceback

from playwright.sync_api import sync_playwright

import os
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
FIXED_NOW = 1_700_000_000_000
TESTS = []


def test(suite='core'):
    def deco(fn):
        TESTS.append((suite, fn.__name__, fn))
        return fn
    return deco


class Quiet(http.server.SimpleHTTPRequestHandler):
    def log_message(self, *a):
        pass


def serve():
    handler = functools.partial(Quiet, directory=ROOT)
    socketserver.TCPServer.allow_reuse_address = True
    srv = socketserver.ThreadingTCPServer(('127.0.0.1', 0), handler)
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    return srv, f'http://127.0.0.1:{srv.server_address[1]}'


DET = f"{{autoPoll:false, now:()=>{FIXED_NOW}, dataSource:createMockDataSource({{now:()=>{FIXED_NOW}}})}}"


def det_env(extra_ds='', extra_env=''):
    ds = f"createMockDataSource({{now:()=>{FIXED_NOW}{extra_ds}}})"
    return f"{{autoPoll:false, now:()=>{FIXED_NOW}, dataSource:{ds}{extra_env}}}"


class Ctx:
    def __init__(self, browser, base):
        self.browser, self.base = browser, base

    def page(self, **kw):
        opts = dict(viewport={'width': 390, 'height': 800}, has_touch=True, is_mobile=True)
        opts.update(kw)
        context = self.browser.new_context(**opts)
        page = context.new_page()
        page.errors = []
        page.on('pageerror', lambda e: page.errors.append(str(e)))
        page.on('console', lambda m: page.errors.append(m.text) if m.type == 'error' else None)
        page.set_default_timeout(5000)
        return page

    def boot(self, env=None, wait='.line.thread', **kw):
        page = self.page(**kw)
        page.goto(self.base + '/tests/browser/harness.html')
        page.wait_for_function('window.ready === true')
        page.evaluate(f"window.app = boot({env or det_env()})")
        if wait:
            page.wait_for_selector(wait)
        return page


NAMES = {'home': 'Home', 'watched': 'Watched', 'feeds': 'Feeds', 'saved': 'Saved', 'history': 'History', 'clips': 'Clips', 'settings': 'Settings'}


def lens(page):
    return page.evaluate("app.store.get().route")


def open_palette(page):
    if not page.evaluate("app.palette.isOpen()"):
        page.evaluate("app.palette.open()")
    page.wait_for_selector('.palette-layer:not([hidden])')


def close_palette(page):
    if page.evaluate("app.palette.isOpen()"):
        page.evaluate("app.palette.close()")


def go(page, name):
    """Switch lens the way a person does: open the controls, tap a view."""
    open_palette(page)
    page.click(f'.pal-lens[data-lens={name}]')
    page.wait_for_function(f"document.querySelector('.lens-name').textContent === '{NAMES[name]}'")


def command(page, text):
    open_palette(page)
    page.fill('.cmd', text)
    page.keyboard.press('Enter')
    page.wait_for_selector('.palette-layer', state='hidden')


def set_filter(page, text):
    open_palette(page)
    page.fill('.cmd', text)
    page.keyboard.press('Enter')
    page.wait_for_selector('.palette-layer', state='hidden')


def clear_filter(page):
    open_palette(page)
    page.click('.clear')
    close_palette(page)


def palette_click(page, name):
    open_palette(page)
    page.locator('.palette .pal-actions').get_by_role('button', name=name).click()
    page.wait_for_selector('.palette-layer', state='hidden')


def titles(page):
    return page.evaluate("[...document.querySelectorAll('.line.thread .title')].map((e) => e.textContent)")


def thread_count(page):
    return page.locator('.line.thread').count()


def expand(page, text=None, nth=0):
    """Tap a line open and wait for its detail to become interactive."""
    loc = page.locator('.line.thread, .line.setting', has_text=text) if text else page.locator('.line.thread')
    line = loc.nth(nth)
    line.locator('.head').click()
    page.wait_for_function("(el) => el.dataset.open === 'true'", arg=line.element_handle())
    return line


def open_line(page, key):
    return page.locator(f'[data-key="{key}"]')


# =============================================================== reconciler
def load_dom_module(page, base):
    page.goto(base + '/tests/browser/harness.html')
    page.wait_for_function('window.ready === true')
    page.evaluate("import('/src/ui/dom.js').then(m => { window.dom = m; })")
    page.wait_for_function('window.dom !== undefined')
    page.evaluate("""() => {
      window.mk = (items) => {
        const ul = window.__ul || (window.__ul = document.body.appendChild(document.createElement('ul')));
        let creates = 0, updates = 0;
        window.dom.reconcile(ul, items, {
          key: (i) => i.id,
          create: () => { creates++; const li = document.createElement('li'); li._born = Math.random(); return li; },
          update: (el, i) => { updates++; el.textContent = i.text ?? i.id; },
        });
        return { order: [...ul.children].map((c) => c.dataset.key), creates, updates, text: [...ul.children].map((c) => c.textContent) };
      };
    }""")


@test()
def reconcile_insert_remove_reorder_and_identity(ctx):
    page = ctx.page()
    load_dom_module(page, ctx.base)
    r = page.evaluate("mk([{id:'a'},{id:'b'},{id:'c'}])")
    assert r['order'] == ['a', 'b', 'c'] and r['creates'] == 3
    page.evaluate("window.ids = Object.fromEntries([...__ul.children].map(c => [c.dataset.key, c._born]))")
    r = page.evaluate("mk([{id:'c'},{id:'a'},{id:'d'},{id:'b'}])")
    assert r['order'] == ['c', 'a', 'd', 'b'] and r['creates'] == 1, r
    same = page.evaluate("[...__ul.children].filter(c => ids[c.dataset.key] === c._born).map(c => c.dataset.key)")
    assert sorted(same) == ['a', 'b', 'c'], 'existing nodes must be reused, not rebuilt'
    r = page.evaluate("mk([{id:'b'}])")
    assert r['order'] == ['b'] and page.evaluate("__ul.children.length") == 1
    r = page.evaluate("mk([])")
    assert r['order'] == []


@test()
def reconcile_updates_text_and_ignores_duplicate_keys(ctx):
    page = ctx.page()
    load_dom_module(page, ctx.base)
    page.evaluate("mk([{id:'a',text:'one'}])")
    r = page.evaluate("mk([{id:'a',text:'two'},{id:'a',text:'dupe'},{id:'b',text:'x'}])")
    assert r['order'] == ['a', 'b'] and r['text'] == ['two', 'x'], r


@test()
def reconcile_preserves_scroll_position_and_focus(ctx):
    page = ctx.page()
    load_dom_module(page, ctx.base)
    page.evaluate("""() => {
      const box = document.body.appendChild(document.createElement('div'));
      box.style.cssText = 'height:200px;overflow:auto;position:fixed;top:0;left:0;width:200px';
      const ul = window.__ul = box.appendChild(document.createElement('ul'));
      window.box = box;
      const make = (n) => Array.from({length: n}, (_, i) => ({id: 'r' + i}));
      window.render = (n) => window.dom.reconcile(ul, make(n), {
        key: (i) => i.id,
        create: () => { const li = document.createElement('li'); const b = document.createElement('button'); b.textContent = 'x'; li.append(b); li.style.height = '40px'; return li; },
        update: () => {},
      });
      render(100);
      box.scrollTop = 800;
      ul.children[20].firstChild.focus();
    }""")
    page.evaluate("render(100)")
    page.evaluate("render(101)")
    assert page.evaluate("box.scrollTop") == 800
    assert page.evaluate("document.activeElement === __ul.children[20].firstChild"), 'focus lost on re-render'



def harness(ctx, **kw):
    page = ctx.page(**kw)
    page.goto(ctx.base + '/tests/browser/harness.html')
    page.wait_for_function('window.ready === true')
    return page


# =============================================================== trust boundary
HOSTILE = [
    "<img src=x onerror=window.__xss=1>",
    "<script>window.__xss=2</script>",
    '"><svg onload=window.__xss=3>',
    "javascript:window.__xss=4",
    "&lt;b&gt;entity&lt;/b&gt;",
    "'; DROP TABLE threads; --",
]


def hostile_ds():
    items = ','.join(
        f"{{key:'lake/gdn/{900001 + i}',source:'lake',board:'gdn',thread:{900001 + i},title:{t!r},replyCount:3,lastModified:{FIXED_NOW + 1000 + i},status:'live',hue:10}}"
        for i, t in enumerate(HOSTILE)
    )
    return det_env(extra_ds=f",extraThreads:[{items}]")


@test()
def hostile_titles_render_as_inert_text_everywhere(ctx):
    page = ctx.boot(hostile_ds())
    seen = titles(page)
    for t in HOSTILE:
        assert t in seen, f'title not rendered verbatim as text: {t}'
    assert page.evaluate("window.__xss") is None
    assert page.evaluate("document.querySelectorAll('[onload],[onerror]').length") == 0
    assert page.evaluate("document.querySelectorAll('ul[aria-label=Stream] img, ul[aria-label=Stream] script, ul[aria-label=Stream] svg[onload]').length") == 0
    line = expand(page, 'onerror')
    assert HOSTILE[0] in line.locator('.title').text_content()
    line.get_by_role('button', name='Open').click()
    page.wait_for_selector('.thread-head')
    palette_click(page, 'Back')
    set_filter(page, 'svg')
    page.wait_for_function("document.querySelectorAll('.line.thread').length === 1")
    assert page.evaluate("document.querySelectorAll('ul[aria-label=Stream] img, ul[aria-label=Stream] script').length") == 0
    go(page, 'history')
    assert HOSTILE[0] in titles(page)
    assert page.evaluate("window.__xss") is None
    assert not page.errors, page.errors


@test()
def no_element_ever_carries_an_inline_event_attribute(ctx):
    page = ctx.boot(hostile_ds())
    for name in ['watched', 'feeds', 'saved', 'history', 'settings', 'home']:
        go(page, name)
    open_palette(page)
    bad = page.evaluate("""() => [...document.querySelectorAll('*')].flatMap((e) => [...e.attributes].filter((a) => a.name.startsWith('on')).map((a) => e.tagName + '@' + a.name))""")
    assert not bad, bad


# =============================================================== failure states
ALERT = '.panel[role=alert]:not([hidden])'


@test()
def first_load_offline_with_nothing_to_show_gets_an_error_panel_then_recovers(ctx):
    page = ctx.boot(det_env(extra_ds=",faults:{offline:true}"), wait='.banner:not([hidden])')
    page.evaluate("app.store.set(s => ({...s, user: {...s.user, watched: {}}}))")
    page.wait_for_selector(ALERT)
    text = page.inner_text(ALERT)
    assert "Can't load right now" in text and 'offline' in text.lower()
    assert thread_count(page) == 0 and page.locator('.line.note').count() == 0
    page.evaluate("app.ds.setFaults({offline:false})")
    page.click(f'{ALERT} button')
    page.wait_for_selector('.line.thread')
    assert page.locator('.panel[role=alert]').is_hidden()


@test()
def first_load_offline_with_local_lines_keeps_them_and_offers_retry(ctx):
    page = ctx.boot(det_env(extra_ds=",faults:{offline:true}"), wait='.banner:not([hidden])')
    assert 'offline' in page.inner_text('.banner').lower()
    assert thread_count(page) == 3, 'watched lines are local data and stay visible'
    assert page.locator('.panel[role=alert]:not([hidden])').count() == 0
    page.evaluate("app.ds.setFaults({offline:false})")
    page.click('.banner button')
    page.wait_for_function("document.querySelectorAll('.line.thread').length > 5")
    page.wait_for_selector('.banner', state='hidden')


@test()
def failed_refresh_keeps_content_and_states_its_age(ctx):
    page = ctx.boot()
    before = thread_count(page)
    assert before > 5
    page.evaluate("app.ds.setFaults({offline:true})")
    palette_click(page, 'Refresh')
    page.wait_for_selector('.banner:not([hidden])')
    assert 'Showing saved data' in page.inner_text('.banner')
    assert thread_count(page) == before
    page.evaluate("app.ds.setFaults({offline:false})")
    page.click('.banner button')
    page.wait_for_selector('.banner', state='hidden')


@test()
def server_error_is_not_an_empty_list(ctx):
    page = ctx.boot(det_env(extra_ds=",faults:{failNext:1}"), wait='.banner:not([hidden])')
    page.evaluate("app.store.set(s => ({...s, user: {...s.user, watched: {}}}))")
    page.wait_for_selector(ALERT)
    assert page.locator('.line.note').count() == 0, 'an error must not render as the empty state'


@test()
def zero_follows_shows_a_note_with_a_path_forward(ctx):
    page = ctx.boot()
    page.evaluate("app.store.set(s => ({...s, user: {...s.user, follows: [], watched: {}}}))")
    page.wait_for_selector('.line.note')
    text = page.inner_text('.line.note')
    assert 'Nothing followed yet' in text and 'Feeds' in text
    go(page, 'feeds')
    page.wait_for_selector('.line.board')


@test()
def empty_watched_has_empty_state(ctx):
    page = ctx.boot()
    page.evaluate("app.store.set(s => ({...s, user: {...s.user, watched: {}}}))")
    go(page, 'watched')
    assert 'Nothing watched yet' in page.inner_text('.line.note')


@test()
def watched_refresh_failure_shows_stale_banner_and_keeps_lines(ctx):
    page = ctx.boot()
    go(page, 'watched')
    page.evaluate("app.ds.setFaults({offline:true})")
    palette_click(page, 'Refresh')
    page.wait_for_selector('.banner:not([hidden])')
    assert thread_count(page) == 3


@test()
def storage_that_throws_does_not_break_the_app(ctx):
    bad = "{getItem(){throw new Error('x')},setItem(){throw new Error('x')},removeItem(){throw new Error('x')}}"
    page = ctx.boot(det_env(extra_env=f",backend:{bad}"))
    assert page.evaluate("app.storage.persistent") is False
    command(page, '/theme dark')
    assert page.evaluate("document.documentElement.dataset.scheme") == 'dark'
    assert not page.errors, page.errors


@test()
def corrupt_persisted_state_falls_back_to_defaults(ctx):
    page = harness(ctx)
    page.evaluate("""() => {
      localStorage.setItem('pulse:user', '{broken');
      localStorage.setItem('pulse:settings', JSON.stringify({v:1, data:{theme:'neon', density:'tiles', refreshSec:'x'}}));
      localStorage.setItem('pulse:overrides', JSON.stringify({v:1, data:'nope'}));
    }""")
    page.evaluate(f"window.app = boot({det_env(extra_env=',backend:localStorage')})")
    page.wait_for_selector('.line.thread')
    s = page.evaluate("app.store.get().settings")
    assert s['theme'] == 'system' and s['density'] == 'regular' and s['refreshSec'] == 30, s
    assert not page.errors, page.errors


# =============================================================== the stream
@test()
def home_leads_with_needs_you_then_latest(ctx):
    page = ctx.boot()
    heads = page.evaluate("[...document.querySelectorAll('.line.hd .hd-label')].map((e) => e.textContent)")
    assert heads == ['Needs you', 'Latest'], heads
    first = page.evaluate("[...document.querySelectorAll('ul[aria-label=Stream] > li')].slice(0, 5).map((e) => e.className.split(' ').filter((c) => ['hd', 'thread', 'is-unread'].includes(c)).join('.'))")
    assert first[0] == 'hd' and all('is-unread' in f for f in first[1:4]), first


@test()
def expanding_a_line_is_exclusive_inert_when_closed_and_toggles(ctx):
    page = ctx.boot()
    first = page.locator('.line.thread').nth(0)
    second = page.locator('.line.thread').nth(1)
    assert page.evaluate("(e) => e.querySelector('.detail-in').inert", first.element_handle()) is True
    first.locator('.head').click()
    page.wait_for_function("(e) => e.dataset.open === 'true'", arg=first.element_handle())
    assert page.evaluate("(e) => e.querySelector('.detail-in').inert", first.element_handle()) is False
    second.locator('.head').click()
    page.wait_for_function("(e) => e.dataset.open === 'true'", arg=second.element_handle())
    assert first.get_attribute('data-open') == 'false', 'only one line may be open'
    second.locator('.head').click()
    page.wait_for_function("(e) => e.dataset.open === 'false'", arg=second.element_handle())
    assert page.locator('[data-open=true]').count() == 0


@test()
def opening_a_watched_thread_clears_unread_and_the_summary(ctx):
    page = ctx.boot()
    go(page, 'watched')
    assert '11 new' in page.inner_text('.sum')
    line = expand(page, nth=0)
    key = line.get_attribute('data-key')[2:]
    line.get_by_role('button', name='Open').click()
    page.wait_for_function(f"app.store.get().user.watched['{key}'].lastSeenReplyCount === app.store.get().user.watched['{key}'].latestReplyCount")
    palette_click(page, 'Back')
    page.wait_for_selector('.line.thread')
    assert '11 new' not in page.inner_text('.sum')
    assert page.evaluate("app.store.get().user.history[0].key") == key
    assert page.locator('.line.thread.is-unread').count() == 2


@test()
def mark_all_read_command_clears_everything(ctx):
    page = ctx.boot()
    command(page, '/mark all')
    page.wait_for_function("document.querySelectorAll('.line.is-unread').length === 0")
    assert 'quiet' in page.inner_text('.sum')
    open_palette(page)
    page.fill('.cmd', '/mark')
    assert page.get_by_role('option', name='Mark all read').count() == 0, 'command disappears when there is nothing to mark'


@test()
def watching_from_home_adds_to_watched_and_unwatching_removes(ctx):
    page = ctx.boot()
    line = expand(page, nth=4)
    key = line.get_attribute('data-key')[2:]
    watch = line.get_by_role('button', name='Watch', exact=True)
    watch.click()
    assert page.evaluate(f"Boolean(app.store.get().user.watched['{key}'])")
    assert line.get_by_role('button', name='Watching').get_attribute('aria-pressed') == 'true'
    line.get_by_role('button', name='Watching').click()
    assert page.evaluate(f"Boolean(app.store.get().user.watched['{key}'])") is False


@test()
def follow_and_favorite_are_independent(ctx):
    page = ctx.boot()
    go(page, 'feeds')
    board = page.locator('.line.board', has_text='Audio')
    board.get_by_role('button', name='Fav', exact=False).click()
    key = board.get_attribute('data-key')[2:]
    st = page.evaluate(f"({{f: app.store.get().user.follows.includes('{key}'), v: app.store.get().user.favorites.includes('{key}')}})")
    assert st == {'f': False, 'v': True}, st
    page.locator(f'[data-key="b:{key}"]').get_by_role('button', name='Follow').click()
    st = page.evaluate(f"({{f: app.store.get().user.follows.includes('{key}'), v: app.store.get().user.favorites.includes('{key}')}})")
    assert st == {'f': True, 'v': True}, st


@test()
def same_board_name_in_two_sources_stays_separate(ctx):
    page = ctx.boot()
    go(page, 'feeds')
    gardens = page.locator('.line.board', has_text='Gardening')
    assert gardens.count() == 2
    subs = [gardens.nth(i).locator('.sub').inner_text() for i in range(2)]
    assert sorted(subs) == ['LAKESIDE /GDN/', 'RIDGELINE /GDN/'] or sorted(s.upper() for s in subs) == ['LAKESIDE /GDN/', 'RIDGELINE /GDN/'], subs
    keys = page.evaluate("[...document.querySelectorAll('.line.board')].map((e) => e.dataset.key)")
    assert len(set(keys)) == len(keys)


@test()
def filter_narrows_lines_and_clearing_restores_them(ctx):
    page = ctx.boot()
    n = thread_count(page)
    set_filter(page, 'tomato')
    page.wait_for_function(f"document.querySelectorAll('.line.thread').length < {n}")
    assert all('tomato' in t.lower() for t in titles(page))
    assert 'match' in page.inner_text('.sr-only')
    clear_filter(page)
    page.wait_for_function(f"document.querySelectorAll('.line.thread').length === {n}")
    set_filter(page, 'zzzzqq')
    page.wait_for_selector('.line.note')
    assert 'No lines match' in page.inner_text('.line.note')


@test()
def feeds_filter_by_source_word(ctx):
    page = ctx.boot()
    go(page, 'feeds')
    set_filter(page, 'ridge')
    page.wait_for_function("[...document.querySelectorAll('.line.board')].every((e) => e.textContent.toLowerCase().includes('ridgeline'))")
    assert page.locator('.line.board').count() == 4


@test()
def lens_switch_reuses_line_elements_and_filter_resets(ctx):
    page = ctx.boot()
    set_filter(page, 'seed')
    page.evaluate("document.querySelector('.line.thread')._probe = 7")
    key = page.evaluate("document.querySelector('.line.thread').dataset.key")
    go(page, 'watched')
    assert page.evaluate(f"document.querySelector('[data-key=\"{key}\"]')._probe") == 7, 'the same line node must move between lenses'
    assert page.input_value('.cmd') == '', 'filter text clears when the lens changes'
    assert page.evaluate("app.stream.state().query") == ''


@test()
def saved_and_history_collect_what_you_do(ctx):
    page = ctx.boot()
    line = expand(page, nth=5)
    key = line.get_attribute('data-key')
    line.get_by_role('button', name='Save', exact=True).click()
    line.get_by_role('button', name='Open').click()
    go(page, 'saved')
    assert page.locator(f'[data-key="{key}"]').count() == 1
    go(page, 'history')
    assert page.locator(f'[data-key="{key}"]').count() == 1
    open_palette(page)
    assert page.locator('.pal-lens[data-lens=saved]').count() == 1


@test()
def empty_saved_and_history_explain_themselves(ctx):
    page = ctx.boot()
    go(page, 'saved')
    assert 'Nothing saved' in page.inner_text('.line.note')
    go(page, 'history')
    assert 'No history' in page.inner_text('.line.note')


WAAPI = """(() => document.getAnimations()
  .filter((a) => !(a instanceof CSSTransition) && !(a instanceof CSSAnimation) && a.effect?.target?.closest?.('ul[aria-label=Stream]')))"""


@test()
def a_line_that_moves_is_animated_with_transform_only(ctx):
    page = ctx.boot()
    key = page.evaluate("[...document.querySelectorAll('.line.thread:not(.is-unread)')][3].dataset.key.slice(2)")
    r = page.evaluate(f"""() => {{
      const t = app.store.get().feed.data.find((x) => x.key === '{key}');
      app.actions.toggleWatch(t);
      app.store.set((s) => ({{...s, user: {{...s.user, watched: {{...s.user.watched, '{key}': {{...s.user.watched['{key}'], latestReplyCount: s.user.watched['{key}'].latestReplyCount + 5}}}}}}}}));
      const anims = ({WAAPI})();
      return {{ n: anims.length, props: anims.flatMap((a) => a.effect.getKeyframes().flatMap((k) => Object.keys(k))),
               moved: Number(document.querySelector('ul[aria-label=Stream]').dataset.moved) }};
    }}""")
    assert r['n'] >= 1 and r['moved'] >= 1, r
    assert set(r['props']) <= {'transform', 'opacity', 'offset', 'computedOffset', 'easing', 'composite'}, r['props']
    assert page.locator(f'[data-key="t:{key}"].is-unread').count() == 1


@test()
def reduced_motion_skips_move_animations(ctx):
    page = ctx.boot(reduced_motion='reduce')
    assert page.evaluate("document.documentElement.dataset.motion") == 'reduce'
    n = page.evaluate(f"""() => {{
      app.actions.setRoute('watched'); app.actions.setRoute('home');
      return ({WAAPI})().length;
    }}""")
    assert n == 0


@test()
def expansion_is_collapsed_by_a_lens_change_and_by_the_line_leaving(ctx):
    page = ctx.boot()
    line = expand(page, nth=1)
    key = line.get_attribute('data-key')
    go(page, 'watched')
    assert page.locator('[data-open=true]').count() == 0, 'a lens change collapses the open line'
    assert page.evaluate("app.stream.state().openId") is None
    line = expand(page, nth=0)
    key = line.get_attribute('data-key')
    line.get_by_role('button', name='Watching').click()
    page.wait_for_function(f"!document.querySelector('[data-key=\"{key}\"]')")
    assert page.evaluate("app.stream.state().openId") is None
    page.evaluate(f"app.actions.toggleWatch(app.store.get().feed.data.find((t) => 't:' + t.key === '{key}'))")
    page.wait_for_selector(f'[data-key="{key}"]')
    assert page.locator(f'[data-key="{key}"]').get_attribute('data-open') == 'false', 'a returning line must come back collapsed'


@test()
def flip_helper_moves_with_transform_and_does_nothing_under_reduced_motion(ctx):
    page = harness(ctx)
    page.evaluate("import('/src/ui/flip.js').then((m) => { window.flip = m; })")
    page.wait_for_function('window.flip !== undefined')
    page.evaluate("""() => {
      const box = document.body.appendChild(document.createElement('div'));
      box.style.cssText = 'position:fixed;top:0;left:0;width:200px;height:300px;overflow:auto';
      const ul = window.ul = box.appendChild(document.createElement('ul'));
      ul.style.cssText = 'margin:0;padding:0;list-style:none';
      window.box = box;
      for (const id of ['a', 'b', 'c', 'd']) { const li = document.createElement('li'); li.dataset.key = id; li.style.height = '40px'; li.textContent = id; ul.append(li); }
      window.shuffle = (reduce) => {
        const before = flip.captureRects(ul, box);
        ul.append(ul.firstElementChild);
        return { r: flip.playFlip(before, ul, box, { reduce }), anims: document.getAnimations().length };
      };
    }""")
    r = page.evaluate("shuffle(true)")
    assert r['r']['moved'] == 0 and r['anims'] == 0, r
    r = page.evaluate("shuffle(false)")
    assert r['r']['moved'] == 4 and r['anims'] >= 4, r
    props = page.evaluate("[...new Set(document.getAnimations().flatMap((a) => a.effect.getKeyframes().flatMap((k) => Object.keys(k))))]")
    assert set(props) <= {'transform', 'opacity', 'offset', 'computedOffset', 'easing', 'composite'}, props
    n = page.evaluate("flip.visibleLines(ul, box).length")
    assert n == 4


# =============================================================== opened thread
def open_thread(page, nth=0, text=None):
    line = expand(page, text, nth=nth) if text else expand(page, nth=nth)
    line.get_by_role('button', name='Open').click()
    page.wait_for_selector('.line.post')
    return line


@test()
def open_turns_the_stream_into_the_threads_posts(ctx):
    page = ctx.boot()
    go(page, 'watched')
    open_thread(page, 0)
    assert page.text_content('.lens-name') == 'Thread'
    assert page.locator('.thread-head').count() == 1 and page.locator('.line.thread').count() == 0
    assert page.locator('.line.post.is-op').count() == 1
    assert page.locator('.line.post').first.get_attribute('class').count('is-op') == 1, 'OP comes first'
    assert page.get_attribute('.cmd', 'placeholder').startswith('filter posts')
    key = page.evaluate("app.store.get().thread.key")
    assert page.evaluate(f"app.store.get().user.history[0].key") == key, 'opening records history'
    assert page.evaluate(f"app.store.get().user.watched['{key}'].lastSeenReplyCount") == page.evaluate(f"app.store.get().user.watched['{key}'].latestReplyCount")


@test()
def back_returns_to_the_same_lens_and_scroll_position(ctx):
    page = ctx.boot()
    page.evaluate("document.querySelector('.scroll').scrollTop = 500")
    page.wait_for_timeout(50)
    line = expand(page, nth=6)
    page.wait_for_timeout(400)
    before = page.evaluate("document.querySelector('.scroll').scrollTop")
    line.get_by_role('button', name='Open').click()
    page.wait_for_selector('.line.post')
    assert page.evaluate("document.querySelector('.scroll').scrollTop") == 0
    palette_click(page, 'Back')
    page.wait_for_selector('.line.thread')
    page.wait_for_function(f"document.querySelector('.scroll').scrollTop === {before}")
    assert page.text_content('.lens-name') == 'Home' and page.evaluate("location.hash") == '#home'


@test()
def escape_swipe_right_and_command_all_leave_the_thread(ctx):
    page = ctx.boot()
    open_thread(page, 2)
    page.keyboard.press('Escape')
    page.wait_for_selector('.line.thread')
    open_thread(page, 2)
    swipe(page, -160, 4)
    assert page.locator('.line.post').count() > 0, 'a left swipe must not navigate away from a thread'
    swipe(page, 160, 4)
    page.wait_for_selector('.line.thread')
    open_thread(page, 2)
    command(page, '/back')
    page.wait_for_selector('.line.thread')
    open_thread(page, 2)
    go(page, 'feeds')
    assert page.evaluate("app.store.get().thread") is None


@test()
def escape_while_typing_a_filter_does_not_close_the_thread(ctx):
    page = ctx.boot()
    open_thread(page, 2)
    open_palette(page)
    page.fill('.cmd', 'zzz')
    page.keyboard.press('Escape')
    assert page.locator('.palette-layer').is_hidden() and page.locator('.thread-head').count() == 1
    assert 'filter “zzz”' in page.text_content('.sum'), 'an applied filter is stated in the status line'
    page.keyboard.press('Escape')
    assert page.locator('.thread-head').count() == 0, 'with the controls closed, Escape leaves the thread'


@test()
def refs_jump_to_the_post_and_missing_refs_say_so(ctx):
    page = ctx.boot()
    open_thread(page, 2)
    ref = page.locator('.pl .ref').first
    no = ref.text_content().replace('>>', '')
    ref.click()
    page.wait_for_function(f"document.querySelector('[data-key=\"p:{no}\"]').classList.contains('flash')")
    assert page.locator('.backs .ref').count() > 0, 'replies show backlinks'
    # filter so the referenced post is hidden, then follow the ref
    set_filter(page, 'basics')
    page.wait_for_function("[...document.querySelectorAll('.pl .ref')].some((r) => !document.querySelector('[data-key=\"p:' + r.textContent.slice(2) + '\"]'))")
    page.evaluate("[...document.querySelectorAll('.pl .ref')].find((r) => !document.querySelector('[data-key=\"p:' + r.textContent.slice(2) + '\"]')).click()")
    assert 'not in this view' in page.text_content('.toast')


@test()
def post_filter_narrows_and_clearing_restores(ctx):
    page = ctx.boot()
    open_thread(page, 2)
    n = page.locator('.line.post').count()
    set_filter(page, 'basics')
    page.wait_for_function(f"document.querySelectorAll('.line.post').length < {n}")
    assert page.locator('.thread-head').count() == 1
    clear_filter(page)
    page.wait_for_function(f"document.querySelectorAll('.line.post').length === {n}")


@test()
def thread_header_watch_save_and_commands_work(ctx):
    page = ctx.boot()
    open_thread(page, 6)
    key = page.evaluate("app.store.get().thread.key")
    head = page.locator('.thread-head')
    head.get_by_role('button', name='Watch', exact=True).click()
    assert page.evaluate(f"Boolean(app.store.get().user.watched['{key}'])")
    command(page, '/save thread')
    assert page.evaluate(f"Boolean(app.store.get().user.saved['{key}'])")
    assert head.get_by_role('button', name='Saved').get_attribute('aria-pressed') == 'true'
    command(page, '/stop watching')
    assert page.evaluate(f"Boolean(app.store.get().user.watched['{key}'])") is False


@test()
def thread_load_states_loading_error_and_stale(ctx):
    page = ctx.boot()
    page.evaluate("app.ds.setFaults({offline:true})")
    page.locator('.line.thread .head').nth(6).click()
    page.locator('.line.thread[data-open=true]').get_by_role('button', name='Open').click()
    page.wait_for_selector(ALERT)
    assert 'offline' in page.inner_text(ALERT).lower() and page.locator('.line.post').count() == 0
    palette_click(page, 'Back')
    page.wait_for_selector('.line.thread')
    page.evaluate("app.ds.setFaults({offline:false})")
    open_thread(page, 6)
    page.evaluate("app.ds.setFaults({offline:true})")
    palette_click(page, 'Refresh')
    page.wait_for_selector('.banner:not([hidden])')
    assert page.locator('.line.post').count() > 3, 'posts stay visible when a refresh fails'


@test()
def hostile_post_text_and_titles_stay_inert_in_a_thread(ctx):
    page = ctx.boot(hostile_ds())
    line = expand(page, 'onerror')
    line.get_by_role('button', name='Open').click()
    page.wait_for_selector('.thread-head')
    assert HOSTILE[0] in page.text_content('.th-title')
    assert page.evaluate("document.querySelectorAll('ul[aria-label=Stream] img, ul[aria-label=Stream] script, [onerror], [onload]').length") == 0
    assert page.evaluate("window.__xss") is None


@test('a11y')
def thread_view_is_labelled_and_back_is_reachable(ctx):
    page = ctx.boot()
    open_thread(page, 2)
    assert page.evaluate("document.querySelector('.palette .btn[aria-label^=\"Back to\"]') !== null")
    assert page.evaluate("document.querySelector('h1.lens-name').textContent") == 'Thread'
    page.wait_for_function("document.querySelector('.sr-only[aria-live]').textContent.startsWith('Thread:')")
    bad = page.evaluate("""() => [...document.querySelectorAll('.scroll button')]
      .filter((e) => e.offsetParent !== null && !e.closest('[inert]') && !e.classList.contains('inline'))
      .map((e) => { const r = e.getBoundingClientRect(); return [e.textContent.trim().slice(0, 20), Math.round(r.width), Math.round(r.height)]; })
      .filter(([, w, h]) => w < 44 || h < 44)""")
    assert not bad, bad


# =============================================================== media
def thread_with_media(page):
    """Open the first thread that has media; its OP always has an image."""
    open_thread(page, 2)
    page.wait_for_selector('.media-line')


def media_ds(media_json, autoplay=False):
    post = "{no:900001,at:%d,author:'Anon',text:'hello',media:%s}" % (FIXED_NOW, media_json)
    t = f"{{key:'lake/gdn/900001',source:'lake',board:'gdn',thread:900001,title:'Media test',replyCount:0,lastModified:{FIXED_NOW + 5},status:'live',hue:10,posts:[{post}]}}"
    return det_env(extra_ds=f",extraThreads:[{t}]")


@test()
def media_is_one_text_line_and_nothing_is_fetched_until_it_is_opened(ctx):
    page = ctx.boot()
    thread_with_media(page)
    line = page.locator('.media-line').first
    assert 'IMG' in line.text_content() and '.jpg' in line.text_content()
    assert page.locator('.post img, .post video').count() == 0, 'closed media must not be loaded'
    line.click()
    page.wait_for_selector('.media[data-open=true] img')
    assert page.evaluate("document.querySelector('.media[data-open=true] img').alt").endswith('.jpg')
    assert page.evaluate("document.querySelector('.media[data-open=true] img').complete") in (True, False)
    page.wait_for_function("document.querySelector('.media[data-open=true] img').naturalWidth > 0")
    line.click()
    page.wait_for_function("document.querySelector('.media[data-open]').dataset.open === 'false'")
    assert page.locator('.media[data-open=true]').count() == 0


@test()
def opening_media_collapses_any_other_open_line_and_survives_refresh(ctx):
    page = ctx.boot()
    thread_with_media(page)
    page.locator('.media-line').first.click()
    page.wait_for_selector('.media[data-open=true]')
    page.locator('.media-line').nth(1).click()
    page.wait_for_function("document.querySelectorAll('.media[data-open=true]').length === 1")
    assert page.evaluate("app.stream.state().openId").startswith('m:')
    page.evaluate("app.actions.refreshAll()")
    page.wait_for_timeout(200)
    assert page.locator('.media[data-open=true]').count() == 1


@test()
def a_video_never_autoplays_unless_the_scoped_setting_says_so(ctx):
    page = ctx.boot()
    thread_with_media(page)
    vid = page.locator('.media-line', has_text='VID').first
    assert vid.count() == 1, 'the demo thread has a video'
    vid.scroll_into_view_if_needed()
    vid.click()
    page.wait_for_selector('.media[data-open=true] video')
    assert page.evaluate("document.querySelector('.media[data-open=true] video').paused") is True
    assert page.evaluate("document.querySelector('.media[data-open=true] video').controls") is True
    vid.click()
    page.evaluate("app.actions.setSetting('autoplay', true)")
    vid.click()
    page.wait_for_function("document.querySelector('.media[data-open=true] video').paused === false")
    assert page.evaluate("document.querySelector('.media[data-open=true] video').muted") is True
    vid.click()
    assert page.evaluate("document.querySelector('.media video').paused") is True, 'collapsing pauses'
    page.evaluate("app.actions.setScopedValue('site:' + app.store.get().thread.snapshot.source, 'autoplay', false)")
    vid.click()
    page.wait_for_selector('.media[data-open=true] video')
    page.wait_for_timeout(200)
    assert page.evaluate("document.querySelector('.media[data-open=true] video').paused") is True, 'a site override beats the app setting'


@test()
def fullscreen_opens_pages_closes_and_restores_focus(ctx):
    page = ctx.boot()
    thread_with_media(page)
    page.locator('.media-line').first.click()
    btn = page.locator('.media[data-open=true]').get_by_role('button', name='Fullscreen')
    btn.click()
    page.wait_for_selector('.viewer:not([hidden])')
    assert page.evaluate("document.querySelector('.shell').inert") is True
    assert page.evaluate("document.activeElement.textContent") == 'Close'
    n = page.evaluate("app.viewer && document.querySelector('.v-count').textContent")
    assert n.startswith('1/')
    assert page.locator('.v-nav button', has_text='Prev').is_disabled()
    page.locator('.v-nav button', has_text='Next').click()
    page.wait_for_function("document.querySelector('.v-count').textContent.startsWith('2/')")
    page.keyboard.press('ArrowLeft')
    page.wait_for_function("document.querySelector('.v-count').textContent.startsWith('1/')")
    page.keyboard.press('Escape')
    page.wait_for_selector('.viewer', state='hidden')
    assert page.evaluate("document.querySelector('.shell').inert") is False
    assert page.locator('.thread-head').count() == 1, 'Escape closes the viewer first, not the thread'
    assert page.evaluate("document.activeElement.textContent") == 'Fullscreen'
    assert page.evaluate("app.store.get().viewer") is None


@test()
def viewer_swipes_page_and_a_downward_drag_dismisses(ctx):
    page = ctx.boot()
    thread_with_media(page)
    page.evaluate("app.actions.openViewer(app.store.get().thread.load.data.posts.find((p) => p.media.length).no + ':0')")
    page.wait_for_selector('.viewer:not([hidden])')
    swipe(page, -160, 4, selector='.v-stage')
    page.wait_for_function("document.querySelector('.v-count').textContent.startsWith('2/')")
    swipe(page, 160, 4, selector='.v-stage')
    page.wait_for_function("document.querySelector('.v-count').textContent.startsWith('1/')")
    swipe(page, 0, 40, selector='.v-stage')
    assert page.locator('.viewer:not([hidden])').count() == 1, 'a short drag must not dismiss'
    swipe(page, 0, 160, selector='.v-stage')
    page.wait_for_selector('.viewer', state='hidden')


@test()
def zoom_toggles_with_transform_only_and_focus_is_trapped(ctx):
    page = ctx.boot()
    thread_with_media(page)
    page.evaluate("app.actions.openViewer(app.store.get().thread.load.data.posts.find((p) => p.media.length).no + ':0')")
    page.wait_for_selector('.viewer:not([hidden])')
    page.wait_for_selector('.v-media')
    page.click('.v-nav >> text=Zoom')
    assert 'scale(2)' in page.evaluate("document.querySelector('.v-media').style.transform")
    assert page.get_attribute('.v-nav >> text=Zoom', 'aria-pressed') == 'true'
    page.click('.v-nav >> text=Zoom')
    assert 'scale(1)' in page.evaluate("document.querySelector('.v-media').style.transform")
    for _ in range(12):
        page.keyboard.press('Tab')
        assert page.evaluate("document.querySelector('.viewer').contains(document.activeElement)"), 'focus escaped the viewer'
    page.keyboard.press('Shift+Tab')
    assert page.evaluate("document.querySelector('.viewer').contains(document.activeElement)")


@test()
def leaving_the_thread_or_losing_the_file_closes_the_viewer(ctx):
    page = ctx.boot()
    thread_with_media(page)
    page.evaluate("app.actions.openViewer(app.store.get().thread.load.data.posts.find((p) => p.media.length).no + ':0')")
    page.wait_for_selector('.viewer:not([hidden])')
    page.evaluate("app.store.set((s) => ({...s, thread: {...s.thread, load: {...s.thread.load, data: {...s.thread.load.data, posts: []}}}}))")
    page.wait_for_selector('.viewer', state='hidden')
    assert page.evaluate("app.store.get().viewer") is None


@test()
def view_media_command_opens_the_viewer_and_only_exists_with_media(ctx):
    page = ctx.boot()
    open_thread(page, 2)
    command(page, '/view media')
    page.wait_for_selector('.viewer:not([hidden])')
    page.keyboard.press('Escape')
    page.wait_for_selector('.viewer', state='hidden')


@test()
def hostile_media_names_are_text_and_unsafe_sources_are_dropped(ctx):
    media = "[{kind:'image',name:'<img src=x onerror=window.__xss=9>.jpg',bytes:5,w:10,h:10,src:'data:image/svg+xml;charset=utf-8,%3Csvg xmlns=%27http://www.w3.org/2000/svg%27 width=%2710%27 height=%2710%27/%3E'},{kind:'image',name:'evil.jpg',src:'javascript:window.__xss=10'},{kind:'image',name:'http.jpg',src:'http://example.test/a.jpg'}]"
    page = ctx.boot(media_ds(media))
    expand(page, 'Media test').get_by_role('button', name='Open').click()
    page.wait_for_selector('.media-line')
    assert page.locator('.media-line').count() == 1, 'unsafe and non-https sources are dropped'
    assert '<img src=x onerror' in page.locator('.media-line').text_content()
    page.locator('.media-line').click()
    page.wait_for_selector('.media[data-open=true] img')
    page.get_by_role('button', name='Fullscreen').click()
    page.wait_for_selector('.viewer:not([hidden])')
    assert page.evaluate("window.__xss") is None
    assert page.evaluate("document.querySelectorAll('.viewer [onerror], .viewer script, .post [onerror]').length") == 0


@test('a11y')
def viewer_is_a_labelled_modal_with_reachable_44px_controls(ctx):
    page = ctx.boot()
    thread_with_media(page)
    page.locator('.media-line').first.click()
    page.locator('.media[data-open=true]').get_by_role('button', name='Fullscreen').click()
    page.wait_for_selector('.viewer:not([hidden])')
    v = page.locator('.viewer')
    assert v.get_attribute('role') == 'dialog' and v.get_attribute('aria-modal') == 'true' and v.get_attribute('aria-label') == 'Media viewer'
    assert page.evaluate("document.querySelector('.v-media').alt").endswith('.jpg')
    small = page.evaluate("""() => [...document.querySelectorAll('.viewer button')].filter((e) => e.offsetParent !== null)
      .map((e) => { const r = e.getBoundingClientRect(); return [e.textContent, Math.round(r.width), Math.round(r.height)]; })
      .filter(([, w, h]) => w < 44 || h < 44)""")
    assert not small, small
    assert 'Image 1 of' in page.text_content('.viewer .sr-only')
    page.keyboard.press('Escape')


# =============================================================== command bar
@test()
def command_bar_is_a_labelled_combobox_that_runs_commands_from_the_keyboard(ctx):
    page = ctx.boot()
    inp = page.locator('.cmd')
    assert inp.get_attribute('role') == 'combobox' and inp.get_attribute('aria-expanded') == 'false'
    open_palette(page)
    inp.fill('/')
    page.wait_for_selector('.suggest:not([hidden])')
    assert inp.get_attribute('aria-expanded') == 'true'
    ctl = inp.get_attribute('aria-controls')
    assert page.evaluate(f"document.getElementById('{ctl}').getAttribute('role')") == 'listbox'
    first = inp.get_attribute('aria-activedescendant')
    assert first and page.evaluate(f"document.getElementById('{first}').getAttribute('aria-selected')") == 'true'
    page.keyboard.press('ArrowDown')
    assert inp.get_attribute('aria-activedescendant') != first
    page.keyboard.press('Enter')
    page.wait_for_function("app.store.get().route === 'watched'")
    assert page.locator('.palette-layer').is_hidden() and page.input_value('.cmd') == ''


@test()
def escape_clears_then_closes_and_slash_ranks_commands(ctx):
    page = ctx.boot()
    open_palette(page)
    page.fill('.cmd', '/dar')
    page.wait_for_selector('.suggest:not([hidden])')
    assert page.locator('.opt').first.inner_text().startswith('Theme: Dark')
    page.keyboard.press('Escape')
    assert page.input_value('.cmd') == '' and page.locator('.palette-layer:not([hidden])').count() == 1, 'first Escape drops the command'
    page.fill('.cmd', '/zzzzqq')
    page.wait_for_selector('.suggest', state='hidden')
    page.keyboard.press('Escape')
    page.keyboard.press('Escape')
    assert page.locator('.palette-layer').is_hidden()


@test()
def tapping_an_option_runs_it_and_confirms_with_a_toast(ctx):
    page = ctx.boot()
    open_palette(page)
    page.fill('.cmd', '/go to feeds')
    page.get_by_role('option', name='Go to Feeds').click()
    page.wait_for_function("app.store.get().route === 'feeds'")
    assert 'Go to Feeds' in page.inner_text('.toast')
    assert page.evaluate("location.hash") == '#feeds'


@test()
def suggestions_refresh_when_state_changes_under_them(ctx):
    page = ctx.boot()
    open_palette(page)
    page.fill('.cmd', '/amoled')
    page.wait_for_selector('.opt')
    assert page.locator('.opt').first.inner_text().startswith('Turn on AMOLED')
    page.evaluate("app.actions.setSetting('amoled', true)")
    page.wait_for_function("document.querySelector('.opt')?.textContent.startsWith('Turn off AMOLED')")


# =============================================================== settings
@test()
def settings_lens_filters_and_restores(ctx):
    page = ctx.boot()
    go(page, 'settings')
    n = page.locator('.line.setting').count()
    assert n == 10, n
    set_filter(page, 'amoled')
    page.wait_for_function("document.querySelectorAll('.line.setting').length === 1")
    clear_filter(page)
    page.wait_for_function(f"document.querySelectorAll('.line.setting').length === {n}")


@test()
def theme_amoled_density_and_reduce_motion_apply_immediately(ctx):
    page = ctx.boot()
    command(page, '/theme dark')
    assert page.evaluate("document.documentElement.dataset.scheme") == 'dark'
    command(page, '/amoled')
    assert page.evaluate("document.documentElement.dataset.amoled") == 'true'
    command(page, '/compact')
    assert page.evaluate("document.documentElement.dataset.density") == 'compact'
    assert page.locator('.line.thread .sub').first.is_hidden()
    command(page, '/reduce motion')
    assert page.evaluate("document.documentElement.dataset.motion") == 'reduce'
    go(page, 'settings')
    sw = page.locator('.line.setting', has_text='AMOLED').locator('.head')
    assert sw.get_attribute('aria-checked') == 'true'
    sw.click()
    assert page.evaluate("document.documentElement.dataset.amoled") == 'false'


@test()
def choice_setting_expands_to_radios_and_applies(ctx):
    page = ctx.boot()
    go(page, 'settings')
    line = expand(page, 'Theme')
    line.get_by_role('radio', name='Dark').click()
    assert page.evaluate("document.documentElement.dataset.scheme") == 'dark'
    assert line.locator('.count').inner_text() == 'Dark'


@test()
def os_reduced_motion_preference_is_honored_at_boot(ctx):
    page = ctx.boot(reduced_motion='reduce')
    assert page.evaluate("document.documentElement.dataset.motion") == 'reduce'


@test()
def os_dark_mode_is_followed_when_theme_is_system(ctx):
    page = ctx.boot(color_scheme='dark')
    assert page.evaluate("document.documentElement.dataset.scheme") == 'dark'


@test()
def host_viewer_theme_wins_over_os_when_theme_is_system(ctx):
    page = ctx.boot(color_scheme='dark')
    page.evaluate("document.documentElement.setAttribute('data-theme', 'light')")
    page.wait_for_function("document.documentElement.dataset.scheme === 'light'")
    command(page, '/theme dark')
    page.evaluate("document.documentElement.setAttribute('data-theme', 'light')")
    page.wait_for_timeout(50)
    assert page.evaluate("document.documentElement.dataset.scheme") == 'dark', 'an explicit choice beats the host'


@test()
def scoped_setting_overrides_one_source_and_resets(ctx):
    page = ctx.boot()
    go(page, 'settings')
    line = expand(page, 'Autoplay')
    line.get_by_role('radio', name='Lakeside').click()
    assert 'App default' not in line.locator('.facts').inner_text() and 'app default' in line.locator('.facts').inner_text().lower()
    line.get_by_role('switch').click()
    assert page.evaluate("app.store.get().overrides['site:lake'].autoplay") is True
    assert page.evaluate("app.store.get().settings.autoplay") is False
    assert 'Overridden for Lakeside' in line.locator('.facts').inner_text()
    line.get_by_role('radio', name='Ridgeline').click()
    assert line.get_by_role('switch').get_attribute('aria-checked') == 'false', 'other sources are unaffected'
    line.get_by_role('radio', name='Lakeside').click()
    line.get_by_role('button', name='Reset').click()
    assert page.evaluate("Object.keys(app.store.get().overrides).length") == 0


@test()
def clear_activity_needs_confirmation_and_keeps_watched(ctx):
    page = ctx.boot()
    expand(page, nth=2).get_by_role('button', name='Open').click()
    assert page.evaluate("app.store.get().user.history.length") == 1
    go(page, 'settings')
    line = expand(page, 'Clear local activity')
    btn = line.locator('.detail .btn')
    btn.click()
    assert btn.text_content() == 'Tap again to confirm'
    assert page.evaluate("app.store.get().user.history.length") == 1
    btn.click()
    assert page.evaluate("app.store.get().user.history.length") == 0
    assert page.evaluate("Object.keys(app.store.get().user.watched).length") == 3
    assert btn.text_content() == 'Clear'


@test()
def clear_activity_confirmation_times_out(ctx):
    page = ctx.boot()
    go(page, 'settings')
    btn = expand(page, 'Clear local activity').locator('.detail .btn')
    btn.click()
    assert btn.text_content() == 'Tap again to confirm'
    page.wait_for_function("[...document.querySelectorAll('.setting .btn')].some((b) => b.textContent === 'Clear')", timeout=6000)


@test()
def settings_persist_across_reload(ctx):
    page = harness(ctx)
    page.evaluate("localStorage.clear()")
    page.evaluate(f"window.app = boot({det_env(extra_env=',backend:localStorage')})")
    page.wait_for_selector('.line.thread')
    command(page, '/compact')
    expand(page, nth=4).get_by_role('button', name='Watch', exact=True).click()
    page.evaluate("app.flush()")
    page.reload()
    page.wait_for_function('window.ready === true')
    page.evaluate(f"window.app = boot({det_env(extra_env=',backend:localStorage')})")
    page.wait_for_selector('.line.thread')
    assert page.evaluate("document.documentElement.dataset.density") == 'compact'
    assert page.evaluate("Object.keys(app.store.get().user.watched).length") == 4


@test()
def simulated_offline_degrades_gracefully_and_recovers(ctx):
    page = ctx.boot()
    command(page, '/offline')
    page.wait_for_selector('.banner:not([hidden])')
    assert thread_count(page) > 5
    command(page, '/normal')
    page.wait_for_selector('.banner', state='hidden')


# =============================================================== navigation + gestures
@test()
def lenses_switch_with_one_stream_and_matching_hash(ctx):
    page = ctx.boot()
    for name, h in [('watched', '#watched'), ('feeds', '#feeds'), ('saved', '#saved'), ('history', '#history'), ('settings', '#prefs'), ('home', '#home')]:
        go(page, name)
        assert page.evaluate("location.hash") == h, (name, page.evaluate("location.hash"))
        assert page.locator('ul[aria-label=Stream]').count() == 1
        assert page.text_content('.lens-name') == NAMES[name]


@test()
def hash_navigation_and_initial_hash(ctx):
    page = harness(ctx)
    page.evaluate("location.hash = '#watched'")
    page.evaluate(f"window.app = boot({det_env()})")
    page.wait_for_selector('.line.thread')
    assert lens(page) == 'watched'
    page.evaluate("location.hash = '#feeds'")
    page.wait_for_function("app.store.get().route === 'feeds'")
    page.evaluate("location.hash = '#nonsense'")
    page.wait_for_timeout(50)
    assert lens(page) == 'feeds', 'an unknown hash must be ignored'
    page.evaluate("app.actions.setRoute('nonsense')")
    assert lens(page) == 'feeds', 'an unknown route must be ignored'


@test()
def rapid_lens_mashing_ends_in_a_consistent_state(ctx):
    page = ctx.boot()
    page.evaluate("""async () => {
      for (const id of ['watched', 'feeds', 'settings', 'saved', 'home', 'history', 'watched', 'feeds', 'home']) { app.actions.setRoute(id); }
    }""")
    page.wait_for_function("document.querySelector('.lens-name').textContent === 'Home'")
    assert page.locator('ul[aria-label=Stream]').count() == 1 and page.evaluate("location.hash") == '#home'
    assert not page.errors, page.errors


def swipe(page, dx, dy, ms=120, x0=200, y0=400, selector='.scroll'):
    page.evaluate("""async ([sel, dx, dy, ms, x0, y0]) => {
      const el = document.querySelector(sel);
      const fire = (type, x, y) => el.dispatchEvent(new PointerEvent(type, {pointerType: 'touch', clientX: x, clientY: y, bubbles: true, cancelable: true, pointerId: 7}));
      fire('pointerdown', x0, y0);
      await new Promise((r) => setTimeout(r, ms));
      fire('pointerup', x0 + dx, y0 + dy);
    }""", [selector, dx, dy, ms, x0, y0])


@test()
def swipe_moves_between_adjacent_lenses_only_when_deliberate(ctx):
    page = ctx.boot()
    swipe(page, -40, 0)
    swipe(page, -160, 160)
    swipe(page, -160, 0, ms=900)
    assert lens(page) == 'home', 'short, diagonal or slow drags must not navigate'
    swipe(page, -160, 4)
    page.wait_for_function("app.store.get().route === 'watched'")
    swipe(page, 160, 4)
    page.wait_for_function("app.store.get().route === 'home'")
    swipe(page, 160, 4)
    assert lens(page) == 'home', 'swiping past the first lens stays put'


@test()
def swipe_is_ignored_on_inputs_and_controls_that_slide(ctx):
    page = ctx.boot()
    go(page, 'settings')
    expand(page, 'Theme')
    swipe(page, -160, 4, selector='.seg button')
    assert lens(page) == 'settings'


@test()
def scroll_position_survives_refresh_and_clock_ticks(ctx):
    page = ctx.boot()
    page.evaluate("document.querySelector('.scroll').scrollTop = 300")
    page.evaluate("app.store.set((s) => ({...s, clock: s.clock + 1}))")
    page.evaluate("app.actions.refreshAll()")
    page.wait_for_timeout(200)
    assert page.evaluate("document.querySelector('.scroll').scrollTop") == 300


@test()
def background_polling_updates_watched_without_flashing_a_banner(ctx):
    env = f"{{autoPoll:false, now:()=>Date.now(), dataSource:createMockDataSource({{now:()=>Date.now()}})}}"
    page = ctx.boot(env)
    go(page, 'watched')
    page.evaluate("app.pollOnce()")
    page.wait_for_timeout(300)
    assert page.locator('.banner:not([hidden])').count() == 0
    assert page.evaluate("app.store.get().watchedLoad.status") == 'ready'


@test()
def offline_poll_backs_off_and_marks_stale(ctx):
    page = ctx.boot()
    go(page, 'watched')
    page.evaluate("app.ds.setFaults({offline:true})")
    page.evaluate("app.pollOnce()")
    page.wait_for_selector('.banner:not([hidden])')
    assert page.evaluate("app.store.get().watchedLoad.status") == 'stale'


# =============================================================== accessibility
def visit_all(page, fn):
    out = {}
    for name in ['home', 'watched', 'feeds', 'saved', 'history', 'settings']:
        go(page, name)
        out[name] = fn(name)
    return out


@test('a11y')
def every_interactive_element_has_an_accessible_name(ctx):
    page = ctx.boot()
    for name in ['home', 'watched', 'feeds', 'settings']:
        go(page, name)
        if name == 'settings':
            expand(page, 'Autoplay')
        bad = page.evaluate("""() => [...document.querySelectorAll('.scroll button, .scroll input')]
          .filter((e) => e.offsetParent !== null && !e.closest('[inert]'))
          .filter((e) => !(e.getAttribute('aria-label') || e.textContent.trim() || e.getAttribute('placeholder')))
          .map((e) => e.outerHTML.slice(0, 80))""")
        assert not bad, (name, bad)


@test('a11y')
def touch_targets_are_at_least_44px_in_both_densities(ctx):
    page = ctx.boot()
    for density in ['regular', 'compact']:
        page.evaluate(f"app.actions.setSetting('density', '{density}')")
        for name in ['home', 'watched', 'feeds', 'settings']:
            go(page, name)
            if name in ('home', 'watched'):
                expand(page, nth=1)
            if name == 'settings':
                expand(page, 'Autoplay')
            small = page.evaluate("""() => [...document.querySelectorAll('.scroll button')]
              .filter((e) => e.offsetParent !== null && !e.closest('[inert]'))
              .map((e) => { const r = e.getBoundingClientRect(); return [e.getAttribute('aria-label') || e.textContent.trim().slice(0, 20), Math.round(r.width), Math.round(r.height)]; })
              .filter(([, w, h]) => w < 44 || h < 44)""")
            assert not small, (density, name, small)


@test('a11y')
def no_horizontal_overflow_with_long_unbroken_titles(ctx):
    long_title = 'A' * 220
    item = f"{{key:'lake/gdn/900001',source:'lake',board:'gdn',thread:900001,title:'{long_title}',replyCount:99999,lastModified:{FIXED_NOW + 5},status:'live',hue:30}}"
    page = ctx.boot(det_env(extra_ds=f",extraThreads:[{item}]"))
    expand(page, 'AAAA')
    for density in ['regular', 'compact']:
        page.evaluate(f"app.actions.setSetting('density', '{density}')")
        over = page.evaluate("document.querySelector('.scroll').scrollWidth > document.querySelector('.scroll').clientWidth + 1")
        assert not over, density


@test('a11y')
def switches_and_radios_expose_state(ctx):
    page = ctx.boot()
    go(page, 'settings')
    assert page.evaluate("[...document.querySelectorAll('[role=switch]')].every((e) => ['true','false'].includes(e.getAttribute('aria-checked')))")
    expand(page, 'Line density')
    groups = page.evaluate("[...document.querySelectorAll('[role=radiogroup]')].map((g) => g.querySelectorAll('[aria-checked=true]').length)")
    assert groups and all(n == 1 for n in groups), groups
    page.get_by_role('radio', name='Regular').focus()
    page.keyboard.press('ArrowRight')
    assert page.evaluate("app.store.get().settings.density") == 'compact'


@test('a11y')
def headings_and_live_regions_announce_the_lens(ctx):
    page = ctx.boot()
    assert page.evaluate("document.querySelector('h1.lens-name').textContent") == 'Home'
    assert page.evaluate("document.querySelectorAll('.line.hd h2').length") >= 2
    go(page, 'feeds')
    page.wait_for_function("document.querySelector('.sr-only[aria-live]').textContent.startsWith('Feeds:')")
    assert page.evaluate("document.querySelector('ul[aria-label=Stream]').getAttribute('aria-label')") == 'Stream'


@test()
def controls_open_centered_by_slash_skip_button_and_contextmenu_and_restore_focus(ctx):
    page = ctx.boot()
    vh = page.evaluate("innerHeight")
    page.keyboard.press('/')
    page.wait_for_selector('.palette-layer:not([hidden])')
    assert page.input_value('.cmd') == '/'
    box = page.locator('.palette').bounding_box()
    cy = (box['y'] + box['height'] / 2) / vh
    assert 0.3 < cy < 0.7, f'palette not centered: {cy:.2f}'
    page.keyboard.press('Escape')
    page.keyboard.press('Escape')
    assert page.locator('.palette-layer').is_hidden()
    page.evaluate("document.querySelector('.skip').click()")
    page.wait_for_selector('.palette-layer:not([hidden])')
    close_palette(page)
    page.locator('.line.thread .head').first.click({button: 'right'} if False else {}) if False else None
    page.evaluate("document.querySelector('.scroll').dispatchEvent(new MouseEvent('contextmenu', {bubbles: true, cancelable: true, clientX: 50, clientY: 300}))")
    page.wait_for_selector('.palette-layer:not([hidden])')
    assert page.evaluate("document.querySelector('.shell').inert || document.querySelector('.shell').hasAttribute('inert')")
    close_palette(page)
    assert page.locator('.line.thread[data-open=true]').count() == 0


@test()
def stream_text_is_not_selectable_except_post_text_so_hold_opens_controls_not_selection(ctx):
    page = ctx.boot()
    us = lambda sel: page.evaluate(f"getComputedStyle(document.querySelector('{sel}')).userSelect")
    assert us('.lens-name') == 'none' and us('.line.thread .title') == 'none'
    open_thread(page, 2)
    page.wait_for_selector('.pl')
    assert us('.pl') == 'text'


@test()
def clip_a_link_see_it_in_clips_export_it_and_it_survives_reload(ctx):
    page = ctx.boot()
    page.evaluate("window.__copied = []; window.__files = []; app.ui.copy = async (t) => { window.__copied.push(t); return true; }; app.ui.download = (n, t, m) => { window.__files.push([n, t, m]); return true; }; 0")
    line = expand(page, nth=1)
    title = line.locator('.title').text_content()
    clip = line.get_by_role('button', name='Clip link')
    assert clip.get_attribute('aria-pressed') == 'false'
    clip.click()
    page.wait_for_function("(el) => el.querySelector('.acts .btn[aria-pressed=true][aria-label=\"Remove clipped link\"]')", arg=line.element_handle())
    assert page.evaluate("app.store.get().user.clips.length") == 1
    go(page, 'clips')
    assert page.text_content('.lens-name') == 'Clips'
    page.wait_for_selector('.line.clip')
    assert page.locator('.line.clip .title').first.text_content() == title
    assert page.locator('.line.clip-tools').count() == 1
    assert '1 line' in page.text_content('.sum'), 'the tools line is not counted as a clip'
    page.get_by_role('button', name='Copy list').click()
    page.wait_for_function("window.__copied.length === 1")
    text = page.evaluate("window.__copied[0]")
    assert title in text and 'https://' in text and text.endswith('\n')
    page.get_by_role('button', name='Download .md').click()
    f = page.evaluate("window.__files[0]")
    assert f[0] == 'clips.md' and f[2] == 'text/markdown' and f[1].startswith('- ['), f
    page.evaluate("app.flush()")
    saved = page.evaluate("app.storage.get('user', null).clips")
    assert len(saved) == 1 and saved[0]['title'] == title, 'clips are persisted with the rest of the user data'


@test()
def clips_lens_line_actions_open_copy_remove_and_the_command_palette_exports(ctx):
    page = ctx.boot()
    page.evaluate("window.__copied = []; app.ui.copy = async (t) => { window.__copied.push(t); return true; }; 0")
    open_thread(page, 2)
    page.locator('.thread-head').get_by_role('button', name='Clip link').click()
    page.wait_for_function("app.store.get().user.clips.length === 1")
    assert page.locator('.thread-head .btn[aria-label="Remove clipped link"]').get_attribute('aria-pressed') == 'true'
    palette_click(page, 'Back')
    go(page, 'clips')
    line = page.locator('.line.clip').first
    line.locator('.head').click()
    page.wait_for_function("(el) => el.dataset.open === 'true'", arg=line.element_handle())
    line.get_by_role('button', name='Copy link').click()
    page.wait_for_function("window.__copied.length === 1")
    assert page.evaluate("window.__copied[0]").startswith('https://')
    command(page, '/export clips: copy')
    page.wait_for_function("window.__copied.length === 2")
    line.get_by_role('button', name='Open thread').click()
    page.wait_for_selector('.line.post')
    assert page.text_content('.lens-name') == 'Thread'
    palette_click(page, 'Back')
    page.wait_for_selector('.line.clip')
    line = page.locator('.line.clip').first
    line.locator('.head').click()
    page.wait_for_function("(el) => el.dataset.open === 'true'", arg=line.element_handle())
    line.get_by_role('button', name='Remove').click()
    page.wait_for_selector('.line.note')
    assert page.locator('.line.note .panel-title').text_content() == 'No clips'
    assert page.locator('.line.clip-tools').count() == 0


@test()
def hostile_clip_titles_and_stored_clips_stay_inert(ctx):
    page = ctx.boot()
    page.evaluate("""() => app.store.set((s) => ({...s, user: {...s.user, clips: [
      {url: 'https://lake.example/gdn/thread/1', key: 'lake/gdn/1', source: 'lake', board: 'gdn', thread: '1', title: '<img src=x onerror=window.__xss=1>', at: 0},
    ]}}))""")
    go(page, 'clips')
    page.wait_for_selector('.line.clip')
    assert page.locator('.line.clip .title').text_content() == '<img src=x onerror=window.__xss=1>'
    assert page.evaluate("window.__xss") is None
    assert page.evaluate("document.querySelectorAll('ul[aria-label=Stream] img').length") == 0
    assert not page.errors, page.errors


@test()
def a_link_in_a_post_gets_a_clip_button_that_toggles_and_lands_in_clips(ctx):
    page = ctx.boot()
    page.evaluate("window.__copied = []; app.ui.copy = async (t) => { window.__copied.push(t); return true; }; 0")
    page.evaluate("""() => app.store.set((s) => ({...s, user: {...s.user, clips: []}}))""")
    open_thread(page, 0)
    page.wait_for_selector('.pl .url')
    assert page.evaluate("document.querySelectorAll('.pl a').length") == 0, 'links are plain text, never anchors'
    url = page.locator('.pl .url').first
    text = url.text_content().replace('Clip', '')
    assert text.startswith('https://pics.example/')
    btn = url.get_by_role('button')
    assert btn.get_attribute('aria-pressed') == 'false'
    btn.click()
    page.wait_for_function("document.querySelector('.pl .url .clipbtn').getAttribute('aria-pressed') === 'true'")
    assert page.evaluate("app.store.get().user.clips.length") == 1
    palette_click(page, 'Back')
    go(page, 'clips')
    line = page.locator('.line.clip').first
    assert line.locator('.title').text_content() == text.replace('https://', '')
    assert 'link ·' in line.locator('.sub').text_content()
    line.locator('.head').click()
    page.wait_for_function("(el) => el.dataset.open === 'true'", arg=line.element_handle())
    assert line.locator('.facts').text_content() == text
    line.get_by_role('button', name='Open thread').click()
    page.wait_for_selector('.line.post')
    assert page.locator('.pl .url .clipbtn').first.get_attribute('aria-pressed') == 'true', 'state is shown when you come back'
    page.locator('.pl .url .clipbtn').first.click()
    page.wait_for_function("app.store.get().user.clips.length === 0")


@test()
def hostile_links_in_posts_stay_inert_text(ctx):
    page = ctx.boot()
    open_thread(page, 0)
    page.evaluate("""() => app.store.set((s) => ({...s, thread: {...s.thread, load: {...s.thread.load, data: {...s.thread.load.data,
      posts: [...s.thread.load.data.posts, {no: 9999999, author: 'Anon', media: [], text: 'https://x.test/<img src=x onerror=window.__xss=1> javascript:alert(1) http://plain.test https://ok.test/a_b"onmouseover="window.__xss=2'}]}}}}))""")
    page.wait_for_selector('.line.post[data-key="p:9999999"]')
    line = page.locator('.line.post[data-key="p:9999999"]')
    assert line.locator('.url').count() == 2, 'only the two https runs are links'
    assert page.evaluate("window.__xss") is None
    assert page.evaluate("document.querySelectorAll('ul[aria-label=Stream] img, ul[aria-label=Stream] a, [onerror], [onmouseover]').length") == 0
    assert 'javascript:alert(1)' in line.text_content() and 'http://plain.test' in line.text_content()
    assert not page.errors, page.errors


@test('a11y')
def text_contrast_meets_wcag_aa_in_light_dark_and_amoled(ctx):
    js = """() => {
      const lum = (c) => { const [r,g,b] = c.match(/\\d+(\\.\\d+)?/g).slice(0,3).map(Number).map((v) => { v /= 255; return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4; }); return 0.2126*r + 0.7152*g + 0.0722*b; };
      const ratio = (a, b) => { const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p); return (x + 0.05) / (y + 0.05); };
      const bgOf = (el) => { for (let e = el; e; e = e.parentElement) { const c = getComputedStyle(e).backgroundColor; if (c && !c.startsWith('rgba(0, 0, 0, 0')) return c; } return getComputedStyle(document.body).backgroundColor; };
      const pairs = ['.line.thread .title', '.line.thread.is-read .title', '.line.thread .sub', '.line.thread .age', '.line.thread .count', '.line.thread .new', '.hd-label', '.lens-name', '.sum', '.hint', '.pal-lens', '.cmd', '.opt-hint', '.panel-hint'];
      return pairs.map((s) => { const el = document.querySelector(s); if (!el) return [s, null]; return [s, Math.round(ratio(getComputedStyle(el).color, bgOf(el)) * 100) / 100]; });
    }"""
    for scheme, amoled in [('light', False), ('dark', False), ('dark', True)]:
        page = ctx.boot()
        page.evaluate(f"app.store.set(s => ({{...s, settings: {{...s.settings, theme: '{scheme}', amoled: {str(amoled).lower()}}}}}))")
        page.evaluate("app.actions.openThread(app.store.get().feed.data[10])")
        open_palette(page)
        page.fill('.cmd', '/th')
        page.wait_for_timeout(100)
        results = page.evaluate(js)
        assert sum(1 for _, r in results if r is not None) >= 9, results
        for sel, r in results:
            assert r is None or r >= 4.5, f'{scheme}/{amoled}: {sel} contrast {r}'


@test('a11y')
def document_has_lang_title_and_viewport(ctx):
    page = ctx.page()
    page.goto(f'file://{ROOT}/dist/index.html')
    assert page.evaluate("document.documentElement.lang") == 'en'
    assert page.title() == 'Pulse'
    assert 'viewport-fit=cover' in page.evaluate("document.querySelector('meta[name=viewport]').content")


# =============================================================== built artifact
@test()
def built_single_file_boots_with_no_errors_and_only_font_requests(ctx):
    page = ctx.page()
    requests = []
    page.on('request', lambda r: requests.append(r.url) if not r.url.startswith(('file:', 'data:')) else None)
    page.goto(f'file://{ROOT}/dist/index.html')
    page.wait_for_selector('.line.thread')
    page.evaluate("window.app = window.__pulse")
    for name in ['watched', 'feeds', 'settings', 'home']:
        go(page, name)
    real = [e for e in page.errors if 'Failed to load resource' not in e]
    assert not real, real
    foreign = [u for u in requests if not u.startswith(('https://fonts.googleapis.com/', 'https://fonts.gstatic.com/'))]
    assert not foreign, foreign


@test()
def built_file_has_no_inline_handlers_or_dynamic_html_sinks(ctx):
    src = open(f'{ROOT}/dist/index.html').read()
    for needle in ['innerHTML', 'outerHTML', 'insertAdjacentHTML', 'document.write', 'eval(', 'new Function', ' onclick=', ' onerror=', 'dangerouslySet']:
        assert needle not in src, f'found {needle}'


# =============================================================== performance (non-blocking)
@test('perf')
def two_thousand_lines_render_within_budget(ctx):
    t0 = time.time()
    page = ctx.boot(det_env(extra_ds=",faults:{hugePerBoard:2000}"))
    page.wait_for_function("document.querySelectorAll('.line.thread').length >= 2000")
    boot_ms = (time.time() - t0) * 1000
    n = page.locator('.line.thread').count()
    assert boot_ms < 4000, f'boot with {n} lines took {boot_ms:.0f} ms'
    rerender = page.evaluate("""() => { const t = performance.now(); app.store.set((s) => ({...s, clock: s.clock + 1})); return performance.now() - t; }""")
    assert rerender < 250, f're-render of {n} lines took {rerender:.0f} ms'
    toggle = page.evaluate("""() => { const t = performance.now(); document.querySelector('.line.thread .head').click(); return performance.now() - t; }""")
    print(f'    perf: boot {boot_ms:.0f} ms, re-render {rerender:.0f} ms, expand one line {toggle:.0f} ms for {n} lines')
    assert toggle < 50, f'expanding a line in a long stream took {toggle:.0f} ms'


@test('perf')
def lens_switch_keeps_frames_inside_budget(ctx):
    page = ctx.boot()
    worst = page.evaluate("""async () => {
      const gaps = []; let last = performance.now(); let run = true;
      const tick = (t) => { gaps.push(t - last); last = t; if (run) requestAnimationFrame(tick); };
      requestAnimationFrame(tick);
      app.actions.setRoute('watched');
      await new Promise((r) => setTimeout(r, 450));
      app.actions.setRoute('feeds');
      await new Promise((r) => setTimeout(r, 450));
      run = false;
      return Math.max(...gaps.slice(2));
    }""")
    print(f'    perf: worst frame gap {worst:.1f} ms')
    assert worst < 100, f'worst frame gap {worst:.1f} ms (generous limit; headless Chrome is software-rendered)'


@test('perf')
def reconciling_two_thousand_keyed_rows_is_cheap(ctx):
    page = ctx.page()
    load_dom_module(page, ctx.base)
    ms = page.evaluate("""() => {
      const items = Array.from({length: 2000}, (_, i) => ({id: 'k' + i}));
      window.mk(items);
      const t = performance.now();
      window.mk([...items].reverse());
      return performance.now() - t;
    }""")
    print(f'    perf: reverse 2000 keyed rows in {ms:.0f} ms')
    assert ms < 200, ms


def main():
    want_perf = '--perf' in sys.argv
    only = [a for a in sys.argv[1:] if not a.startswith('--')]
    srv, base = serve()
    results = {'pass': 0, 'fail': [], 'perf_fail': []}
    with sync_playwright() as p:
        browser = p.chromium.launch(executable_path='/usr/bin/google-chrome', args=['--no-sandbox', '--no-proxy-server'])
        ctx = Ctx(browser, base)
        for suite, name, fn in TESTS:
            if suite == 'perf' and not want_perf:
                continue
            if only and not any(o in name for o in only):
                continue
            try:
                fn(ctx)
                print(f'PASS  [{suite}] {name}')
                results['pass'] += 1
            except Exception as e:  # noqa: BLE001
                tb = traceback.format_exc().strip().splitlines()
                print(f'FAIL  [{suite}] {name}\n      {type(e).__name__}: {str(e)[:300]}\n      {tb[-3].strip() if len(tb) > 3 else ""}')
                (results['perf_fail'] if suite == 'perf' else results['fail']).append(name)
            finally:
                for c in list(browser.contexts):
                    c.close()
        browser.close()
    srv.shutdown()
    print(f"\n{results['pass']} passed, {len(results['fail'])} failed (blocking), {len(results['perf_fail'])} perf warnings (non-blocking)")
    sys.exit(1 if results['fail'] else 0)


if __name__ == '__main__':
    main()
