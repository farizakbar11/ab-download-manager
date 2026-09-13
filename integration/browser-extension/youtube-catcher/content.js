(function () {
  'use strict';

  const SERVER_URL = 'http://localhost:15151/add';
  const BUTTON_ID = 'abdm-yt-catcher-btn';

  function getVideoTitle() {
    const titleEl = document.querySelector('h1.ytd-watch-metadata yt-formatted-string') ||
                    document.querySelector('#title h1 yt-formatted-string') ||
                    document.querySelector('h2.title.ytd-shorts-player');
    const raw = (titleEl && titleEl.textContent) ? titleEl.textContent : document.title.replace(' - YouTube', '');
    return raw.replace(/[\\/:*?"<>|]/g, ' ').replace(/\s+/g, ' ').trim();
  }

  function isWatchPage() {
    return window.location.pathname === '/watch' || window.location.pathname.startsWith('/shorts/');
  }

  function injectCatcherButton() {
    if (!isWatchPage()) {
      removeCatcherButton();
      return;
    }

    if (document.getElementById(BUTTON_ID)) {
      return; // Already exists
    }

    const subscribeBtn = document.querySelector('#owner #subscribe-button') ||
                         document.querySelector('#subscribe-button') ||
                         document.querySelector('ytd-watch-metadata #owner');

    const btn = document.createElement('div');
    btn.id = BUTTON_ID;
    btn.className = 'abdm-floating-catcher';
    btn.innerHTML = `
      <div class="abdm-catcher-inner">
        <svg class="abdm-catcher-icon" viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
          <path d="M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96zM17 13l-5 5-5-5h3V9h4v4h3z"/>
        </svg>
        <span class="abdm-catcher-text">Download Video</span>
      </div>
    `;

    btn.addEventListener('click', async (e) => {
      e.stopPropagation();
      e.preventDefault();

      const textSpan = btn.querySelector('.abdm-catcher-text');
      const originalText = 'Download Video';
      textSpan.textContent = 'Membuka 4get Download Manager...';
      btn.classList.add('abdm-loading');

      const videoUrl = window.location.href;
      const title = getVideoTitle();

      const payload = {
        items: [
          {
            link: videoUrl,
            downloadPage: videoUrl,
            suggestedName: title
          }
        ],
        options: {
          silentAdd: false,
          silentStart: false
        }
      };

      try {
        const response = await fetch(SERVER_URL, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify(payload)
        });

        if (response.ok) {
          textSpan.textContent = '✅ Berhasil Dikirim!';
          setTimeout(() => {
            textSpan.textContent = originalText;
            btn.classList.remove('abdm-loading');
          }, 2500);
        } else {
          throw new Error('Server error: ' + response.status);
        }
      } catch (err) {
        console.error('[ABDM YouTube Catcher] Error:', err);
        textSpan.textContent = '❌ Pastikan AB Download Manager Aktif';
        setTimeout(() => {
          textSpan.textContent = originalText;
          btn.classList.remove('abdm-loading');
        }, 3500);
      }
    });

    if (subscribeBtn) {
      subscribeBtn.insertAdjacentElement('afterend', btn);
    } else {
      const actionsBar = document.querySelector('#top-level-buttons-computed') ||
                         document.querySelector('#actions-inner') ||
                         document.querySelector('#actions') ||
                         document.querySelector('#player-container');
      if (actionsBar) {
        actionsBar.appendChild(btn);
      }
    }
  }

  function removeCatcherButton() {
    const btn = document.getElementById(BUTTON_ID);
    if (btn) btn.remove();
  }

  // Hook into YouTube's single page app navigation events
  window.addEventListener('yt-navigate-finish', () => {
    setTimeout(injectCatcherButton, 800);
  });
  window.addEventListener('spfdone', () => {
    setTimeout(injectCatcherButton, 800);
  });

  // MutationObserver fallback to catch player rendering
  const observer = new MutationObserver(() => {
    if (isWatchPage() && !document.getElementById(BUTTON_ID)) {
      injectCatcherButton();
    }
  });

  observer.observe(document.body, {
    childList: true,
    subtree: true
  });

  // Initial check
  setTimeout(injectCatcherButton, 1000);
})();
