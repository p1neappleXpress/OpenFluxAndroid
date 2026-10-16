# Changelog

All notable changes to OpenFluxAndroid. Format loosely follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

## [3.8.2] - 2026-10-16 (OpenFluxTeam/OpenFluxAndroid)

Core 0.14.0 / node-v1.7.0 (unchanged).

### Changed

- **One core, no fork / original choice.** Settings → Core (desktop) offers «Встроенное», «Выпуск с GitHub» and «Свой файл»; the release comes from OpenFluxTeam/OpenFlux. The node wizard's «Ядро на сервере» switch is gone: a node always gets that core. Settings saved with «Форк» or «Оригинал» become «Выпуск с GitHub»; a core downloaded earlier as «Форк» keeps working until the next download; one downloaded as «Оригинал» is downloaded again.

## [3.8.1] - 2026-10-16 (OpenFluxTeam/OpenFluxAndroid)

Core 0.14.0 / node-v1.7.0 (the same code as 3.8.0; only the core's README changed).

### Changed

- **Settings → About: no link to the fork.** The «Source code» section no longer opens the fork's core page; it links the core's source («Исходный код ядра») and this app.
- **READMEs** of the core, the shared module, Desktop and Android describe the current release and core (direct first, speed, stripe groups) and link the OpenFluxTeam repositories.

## [3.8.0] - 2026-10-16 (OpenFluxTeam/OpenFluxAndroid)

Core 0.14.0 / node-v1.7.0.

### Added

- **Stripe groups in the core** (p1neappleXpress): several carriers of one type or of mixed types work as one stripe, one lane each. Set up in a `.conf` with `Lanes = N` for now; the app has no screen for it yet.
- **Reno on direct connections.** While the traffic goes over a plain TCP stream the tunnel opens new connections with Reno instead of CUBIC: a single download is faster (+20 % on the test rig, 6-7 against 3.5-4.5 MB/s over a real path) because nothing is lost inside such a tunnel and CUBIC left slow start too early. Other carriers keep CUBIC.

### Removed

- **The bonding switch** (p1neappleXpress): the core no longer splits a session over all carriers at once; the carriers fail over by priority, as before the switch was turned on. Profiles saved with bonding still open. The network a carrier uses (mobile data, Wi-Fi) stays.

## [3.7.1] - 2026-10-16 (OpenFluxTeam/OpenFluxAndroid)

Core 0.13.1 / node-v1.6.1.

### Fixed

- **A restarted node is taken over at once.** After a node restarted (an update, a crash) the app waited 30 seconds of silence to notice («exit silent on every carrier for 30s: handshaking again»), and did not notice at all when the connection was younger than ten seconds. The node now tells the app it has no session, and the connection comes back in a few seconds. Nodes need node-v1.6.1.

## [3.7.0] - 2026-10-16 (OpenFluxTeam/OpenFluxAndroid)

Core 0.13.0 / node-v1.6.0 (the speed work, see TRANSPORT_PERFORMANCE.md in the core).

### Changed

- **A live direct connection is used first.** Profiles ranked direct below the document carriers, so the traffic went through a third party while a straight path worked. The core now sends over a live direct whatever the profile says, falls back to the next carrier when it stops answering and returns when it comes back. A direct transport added to a profile starts at priority 110. A node must be updated too (node-v1.6.0) for the node-to-device direction.
- **Much faster direct connections**: about 1.7 times the download speed and twice the parallel download speed over a real path, the CPU of the node and of the device per megabyte cut by three to seven times (large packets on direct, fewer copies, no compression of what does not compress).

### Fixed

- **Screens no longer show through each other when you go back or forward** (the stock cross-fade drew both at half strength, their texts on top of each other); a screen now slides a little and fades, on an opaque page.
- The icons in a transport's header (remove, open) are on one line.
- **A full VPN interface queue no longer ends the tunnel.** Writing a packet to the interface failed with ENOBUFS under load and the whole VPN stopped ("Запись в VPN-интерфейс: write failed: ENOBUFS"). The write now waits a moment and drops the packet after a few tries, as a busy network card would; TCP sends it again.
- Telemost's idle frames no longer fill the journal with «the peer sends classic-mode frames».

## [3.6.3] - 2026-10-15 (OpenFluxTeam/OpenFluxAndroid)

3.6.2 was not published: its build stopped on a test that still expected the old rule. Its fixes ship here.

### Fixed

- The tests follow the new rule for a place without documents (it connects through cups.online and Avito).

## [3.6.2] - 2026-10-15 (OpenFluxTeam/OpenFluxAndroid)

### Fixed

- **The purchase picks another way to pay.** The ways were shown but a tap on them did nothing, so the first one was always used.
- **A place connects without documents of its own.** «Подключить» and «Запустить» no longer wait for a document: without one the connection goes through cups.online and Avito, which need no account. A document can still be added for another transport.

## [3.6.1] - 2026-10-15 (OpenFluxTeam/OpenFluxAndroid)

### Changed

- **«Это устройство» is no longer listed under «Ноды».** The row that made this device a node is hidden from the servers list.

## [3.6.0] - 2026-10-15 (OpenFluxTeam/OpenFluxAndroid)

### Added

- **The node wizard makes a Telemost conference.** «Создать в Телемосте» opens telemost.yandex.ru in the built-in browser with the Yandex account; press «Создать видеовстречу» there and the conference link goes into the field by itself.
- **Avito Code and cups.online rooms with a button.** With the transport on, the wizard shows the code of its rooms: «Создать комнаты» makes them at once (on the server, as when applying), «Новые комнаты» replaces them, and a code can be pasted to reuse rooms. A changed code counts as a change of the node.

## [3.5.1] - 2026-10-15 (OpenFluxTeam/OpenFluxAndroid)

### Fixed

- **A purchase paid at once no longer opens the browser.** The free way settles the invoice on arrival and gives only a placeholder link, which led to telegram.org; now the sheet goes straight to «Оплачено» and the connection comes by itself. A real way to pay (Platega, CryptoPay) still opens its page.

## [3.5.0] - 2026-10-15 (OpenFluxTeam/OpenFluxAndroid)

### Added

- **«Мои подключения» of the account as a section**: each place of the subscription with «Настроить», «Запустить» and «Остановить». A manual place has its own documents, and its connection is made from all of them in their order; they can be changed only while the place holds no live connection.
- **The ways to pay in the purchase**: when the service offers more than one, the app asks which, and the choice goes with the invoice.

### Changed

- **Core 0.12.0 (node-v1.5.1)**: p1neappleXpress 0.7.3 merged, the Mail.ru carrier packs up to 500 KB into each chat message (tens of KB/s before, about 800 KB/s per burst now). Wire-compatible with older nodes.

## [3.4.2] - 2026-10-15 (OpenFluxTeam/OpenFluxAndroid)

### Fixed

- **A connection of the bot the app cannot read is said with its reason** (for example the transport the app's core does not know), not just «обновите приложение»; one unreadable connection no longer stops the others.
- **No connection because of a slot is explained in the service's own words**, and a paused connection no longer makes the app wait for it forever.
## [3.4.1] - 2026-10-14 (OpenFluxTeam/OpenFluxAndroid)

### Changed

- **The journal says whether the node really works.** «Канал до ноды поднят» when the link is up; in the VPN mode the app cannot check the address itself, so the line says how: open api.ipify.org in the browser, it must show the node's address. The core's useful lines (key confirmed, encryption on, fall back to the classic mode, a captcha is asked for) are also said in plain Russian next to the core's own text.
- Version aligned with the desktop app (the desktop sign-in fix does not touch Android).
## [3.4.0] - 2026-10-14 (OpenFluxTeam/OpenFluxAndroid)

### Changed

- **Signing in is on the OpenFlux page (id.openflux.dev):** by email or through Telegram. «Войти» opens the page in a Custom Tab and brings you back; the subscription and the connection appear by themselves. The sign-in through confirmation in the bot (3.3.0) is gone, the service no longer has it. A session from 3.3.0 keeps working. It needs the service's app sign-in (`auth/app/exchange`) and, in the build, an app id (`-Popenflux.appId=ofa_…` or `OPENFLUX_APP_ID`).

## [3.3.0] - 2026-10-14 (meepo161/OpenFluxAndroid)

### Changed

- **Signing in is through Telegram only.** «Войти через Telegram» opens the bot,
  you confirm the sign-in there and come back: the subscription and the
  connection appear by themselves, no link to paste. The password sign-in,
  registration and recovery key are gone (an account already signed in keeps
  working). It needs the service's app sign-in (`auth/app/start`) and an app
  key in the build (`-Popenflux.appKey=ofk_…` or `OPENFLUX_APP_KEY`).

### Known

- This build is published without an app key, because the service's app sign-in is not live yet: «Войти через Telegram» says that sign-in is off. A build with a key follows once the service is deployed.

## [3.2.1] - 2026-10-14 (meepo161/OpenFluxAndroid)

### Fixed

- The 3.2.0 build did not compile (a property's setter landed on the wrong
  line); 3.2.1 is 3.2.0 as described below.

## [3.2.0] - 2026-10-14 (meepo161/OpenFluxAndroid)

### Added

- **The OpenFlux bot.** A connection bought in @openflux_bot comes in by its
  link (link.openflux.dev/…: paste it, scan it, or open it from Telegram) and stays current by
  itself; when the bot no longer serves it (the subscription ended or the
  connection was replaced) the app says so and leads to the bot for the new
  one. «Войти через Telegram» registers and signs in with the bot, and the
  subscription bought there shows on the subscription screen. Buying stays in
  the bot for now.
- **The node wizard installs every transport**: Yandex Telemost (a conference
  link), Avito Code (its rooms are made at once, no sign-in) and the signed JS
  transports installed in the app (with the experimental features on: the app
  sends the package, the node keeps it updated). A node's profile gets its JS
  transports too.
- **Avito Code** in profiles and subscriptions.

### Fixed

- **A new subscription's location showed only after «Обновить».** The service
  makes a personal node on the first request and answers with no location
  until it is up; the app now asks again every 15 seconds while the location
  is being made, and Home says «Готовим вашу локацию».

### Changed

- Core OpenFlux 0.11.0 (p1neappleXpress 0.7.2: an Avito connection that drops
  no longer crashes the app). Nodes install node-v1.5.0.

## [3.1.0] - 2026-10-13 (meepo161/OpenFluxAndroid)

### Added

- **A payment survives closing the app.** An order waiting to be paid is kept on
  the device and asked about first on every refresh, so paying in a bank's app
  while OpenFlux is closed extends the subscription on the next start. The
  subscription card says "Ждём оплату заказа …" with "Проверить" and "Открыть
  оплату снова".
- **The subscription service can move.** When it announces a new address, the
  app follows it once the new address answers with its certificate, and stays
  signed in.
- **Yandex Telemost in profiles and subscriptions.** A link with Telemost no
  longer loses the location; the app tells the service which transports it runs,
  so a subscription never sends one it cannot use.
- **Share a link to any app.** Giving a profile and the running node offer
  the system share sheet (any messenger or mail), besides copying the link.
- **The exit node on Home.** While nothing runs, a "Клиент / Выходная нода" switch
  above the button shows the node's controls, QR and link right there.

### Changed

- Core OpenFlux 0.10.0 (with p1neappleXpress 0.7.1): the avito carrier, and
  cups.online over several rooms at once — about 4× faster on a node that prints
  the new room list.

## [3.0.1] - 2026-10-13 (meepo161/OpenFluxAndroid)

### Fixed

- **Mail.ru works again.** The Mail.ru editor server now closes the socket on the
  synthetic cursor writes the carrier used, so every connection dropped with
  "access deny". The core (OpenFlux 0.9.0, with p1neappleXpress 0.6.1) carries
  the tunnel over the document's chat channel instead; the scripted Mail.ru
  carrier connects again too. The node must run node-v1.4.2 or newer — nodes
  with auto-update take it by themselves.

## [3.0.0] - 2026-10-13 (meepo161/OpenFluxAndroid)

### Changed

- **The whole interface is new**, written from scratch in the spirit of
  AmneziaVPN: the same functions, much less text on the screen. Four places —
  **Главная**, **Серверы**, **Поделиться**, **Настройки** — and one **＋** for
  every way to add a connection (a key, a link, a file or a QR code; your own
  server; a node on a web hosting without a server; an OpenFlux account; by
  hand). Back goes back within a place and each place keeps its own screens;
  in a wide window a list and the chosen item sit side by side.
- **Главная**: one button, the chosen connection under it (tap to pick another),
  the switches that matter, at most one message at a time, and the connection's
  details folded under «Сведения». A profile's dot is green only while connected.
- **Your own server** (Серверы): log in once (password or key, sudo, the server's
  key fingerprint confirmed), see every node on it, add one in one tap («Быстро»:
  direct only, L3 when the server supports it) or set it up, and **change a
  node in place** — transports, L3/L4, documents, the Yandex sign-in — with the
  number of changes shown and nothing sent until «Применить». Key, port and
  context stay, so links already given out keep working. A new node is proven by
  connecting through it before it is called done.
- **Nodes on a web hosting** without a server: upload by FTP or add one already
  there by its page address; start, stop, its panel, its key.
- **Настройки** are one list of pages: Подключение, VPN и прокси, Аккаунт
  OpenFlux, Сервисы (Yandex and Mail.ru sign-ins), Ядро, Внешний вид, Журнал,
  Экспериментальное, О программе. The journal has search, filters
  (Все/Важные/Ошибки), copy of what is shown and masking of addresses and keys.
- Connecting is one rule everywhere — the button, Ctrl+Enter, the quick-settings tile and
  «Подключаться при запуске» connect the chosen profile as a client; the exit
  node starts only from «Это устройство», and stopping it returns to client mode.
- The notification says the state in the same words as Главная.
- A new release can be skipped («⋮ → Пропустить эту версию»), as before.

### Fixed

- Two servers no longer share one SSH connection, and more than five servers are
  remembered.
- The Yandex sign-in goes to a node only together with the document it was made
  for, not with a different document pasted afterwards.
- A hosting node whose check was not provable on Android is no longer reported
  as failed; files left on a hosting after a failed install can be removed, or
  the install retried without typing the FTP password again.
- A JS transport with an invalid signature can no longer be installed.
- Texts that promised more than the app does: JS transports run **without** a
  sandbox (the old Settings text said otherwise), and the VPN text no longer
  claims that nothing can leak.

### Removed

- The old interface and its navigation library (Voyager).

## [2.14.2] - 2026-10-11 (meepo161/OpenFluxAndroid)

### Fixed

- A server typed in under Ноды → Добавить сервер keeps its connection and takes
  its own address as soon as it connects: the screens opened from it said
  «← Новый сервер» instead of «← root@host». Found by driving the app against a
  real server over SSH.
- Wording that still named screens that are gone: the share hint said «Профили →
  Импорт» (it is Подключения → Добавить), and a node made from a server's screen
  no longer says its profile is needed to reach the node (the server's screen can
  make it later with «Подключить себе»).

## [2.14.1] - 2026-10-11 (meepo161/OpenFluxAndroid)

### Fixed

- A text link that does not fit on one line (the one under "Своих серверов пока
  нет" on a phone with a large font) was cut off; it now takes the lines it needs.

## [2.14.0] - 2026-10-11 (meepo161/OpenFluxAndroid)

### Added

- **Сервер** is a scenario of its own under Ноды. Enter the address and way in
  of a server once; its screen lists every node on it (state, port, mode,
  transports) and from there each can be **changed** (transports, L3/L4, the
  Yandex document; key, port and context stay, issued links keep working),
  **connected** to this device, **shared** (QR and link), or **deleted**, and a
  **new node** can be added without typing the way in again. A node that has no
  profile here gets one made from the key the server reports. Servers entered
  once are remembered (address only) in the Ноды list.

### Changed

- The node wizard only makes a new node (3 steps). Changing installed nodes is
  gone from it and from the profile; a node's profile has "Управлять сервером"
  that opens the server's screen. The core's `node-install.sh show` now returns
  the channel's key to root over SSH (core 0.8.0).

## [2.13.0] - 2026-10-11 (meepo161/OpenFluxAndroid)

### Changed

- The whole navigation is rebuilt around four places, each with its own stack of
  screens: **Главная** (connect), **Подключения** (everything one connects with:
  the OpenFlux account, keys, links, profiles), **Ноды** (what one runs: the
  nodes on one's own servers or hosting), **Настройки**. Profiles, making a node
  and changing a node are separate scenarios with their own screens:
  a profile's screen and its fields editor under Подключения; the list of one's
  nodes, a node's screen, "Изменить на сервере" and the wizards under Ноды.
  A screen opens on top of the place the person is in, so "back" returns to
  where they came from ("← Подключения", "← Нода Амстердам"); a tap on a place's
  tab shows where they left it, a second tap its first screen. In a wide window
  the list stays at the left and a profile or node opens beside it. The
  Подписка tab is gone: the account, its sign-in and the ways to connect live
  under Подключения ("Добавить"). See the README's "Navigation".
- A node is changed on one screen: its profile, the way into its server and the
  transports of its channel, with "Применить на сервере" always in reach; no
  more steps. "Это моя нода" marks an imported profile as one's own to manage.
- Sign-ins to Yandex and Mail.ru are a normal page of Settings (not hidden behind
  developer mode): sign in once and the transports get the session (fewer Yandex
  checks) and one's own node gets it when its session ends. The Yandex check's
  dialog offers it, and "Позже" keeps further checks quiet for ten minutes;
  on Android a postponed check no longer fails a connection the other
  transports can still bring up.
- Home shows the subscription as a strip without a badge on a phone; the
  account opened from Home goes back to Home.

### Added

- Node mode, **L4 or L3**, is chosen when a node is made and changed on an
  installed node (core 0.7.0): l4 (default) needs nothing; l3 passes IP packets
  and asks the server for iptables and systemd 231+, said before installing.
  Only this fork's installer takes it.
- Android: the quick-settings tile "OpenFlux" connects the selected profile or
  disconnects from the status bar's panel and shows the profile and state; the
  first time (or with no profile) it opens the app, which asks for the VPN
  permission.

## [2.12.1] - 2026-10-11 (meepo161/OpenFluxAndroid)

### Fixed

- Account: the device list no longer says this device was last seen minutes
  ago; it is shown as "сейчас на связи".

## [2.12.0] - 2026-10-11 (meepo161/OpenFluxAndroid)

### Added

- Accounts and subscriptions. People who run no node of their own sign in and
  press "Подключить": the owner's service (`opensub`, core 0.6.0,
  docs/subscriptions.ru.md) gives them locations built from the owner's nodes,
  without documents, keys or SSH. Account with a login and password (no
  email), a recovery key shown once, a trial, a plan chosen in the app and paid
  by the owner's own means (the owner confirms the order and the app notices
  it by itself), devices per plan, password change, sign-out, account deletion.
  A subscription key (`openflux://sub?…`, from a link, QR, file or the paste
  field) works without an account. The service is pinned by its certificate's
  SHA-256 carried in the key.
- "Подключение" (the Подписка tab before signing in), after Amnezia's screen:
  one paste field for a key or link, and the other ways to connect under it
  (an account, a node on your own server, a file, a QR code, a node without a
  server, by hand, restore access). Home shows the same two buttons until there
  is a connection, and a strip with the subscription's end date after.
- Change a node from its profile: "Изменить транспорты на сервере…" in the
  profile's details and in its settings opens the node wizard on that
  profile's channel (found on the server by its port); only the server's
  password is asked. The key, port and links stay, the profile is updated.
- The app updates itself from its GitHub releases: a few seconds after the
  start and every six hours it looks for a newer release (switch in
  Settings → О программе) and offers it in a banner with "Обновить",
  "Пропустить" and "Позже". Nothing is downloaded before the tap; the
  installer is checked against the release's `SHA256SUMS.txt` and refused
  without a matching sum; only GitHub's hosts over https are used.
- Android installs through the system installer (the person confirms; "Install
  unknown apps" is allowed for OpenFlux first), picks the APK of the phone's
  CPU, and reports why when the system refuses (a different signing key,
  cancelled, no space).

### Changed

- "Логи" is no longer a tab: the journal opens from Settings → Интерфейс and
  from Home ("Журнал"). Fewer tabs, the same screen.
- "Позже" on a Yandex check now keeps further Yandex checks quiet for ten
  minutes (the other transports carry on) instead of asking again at once.
- Forms clear their error as soon as a field is edited; the recovery key is
  shown four groups to a line.
- The tab bar hides while the keyboard is up.

### Removed

- The "Ночные сборки" switch and the link to a stale Android repository in
  About: this fork publishes one channel.

## [2.11.1] - 2026-10-10 (meepo161/OpenFluxAndroid)

### Fixed

- Node wizard: after changing the documents of a channel already on the
  server, the done step names that channel and its port (it showed the new
  channel the wizard had prepared, port 0).

## [2.11.0] - 2026-10-10 (meepo161/OpenFluxAndroid)

### Added

- Node wizard: change the documents of a channel already on the server. The
  transports step lists the server's channels; "Изменить транспорты" reads one
  and "Применить" adds or removes documents in place. The key, port and
  encryption context stay, so links handed out keep working. With the
  channel's profile on this device (made by the wizard or imported from its
  link) the wizard builds the new link with the channel's context, checks the
  channel through it and updates the same profile.
- From p1neappleXpress 2.3.x: script (JS) transports behind "Экспериментальные
  функции", the "без сервера" PHP-hosting wizard, nightly release channel.

### Changed

- Core 0.5.1 (meepo161/OpenFlux c36eb0c) with p1neappleXpress 0.4.2 merged in;
  the node wizard installs node-v1.4.1.
- Integrated current upstream/main with fork and bonding; retained document
  creation, Yandex sign-in, accounts (a fresh sign-in goes on to your own node)
  and developer mode from the fork.

### Fixed

- Mail.ru Docs carrier joins documents again (the server refused a Chrome user
  agent with 409).
- cups.online: a lost message no longer stalls the stream.
- The Accounts tab follows developer mode again next to the Transports tab of
  the experimental features.

## [2.10.0] - 2026-09-30 (meepo161/OpenFluxAndroid)

### Added

- Бондинг: переключатель в профиле Session. Все транспорты работают
  одновременно: скорости складываются, а если сеть или документ пропадёт,
  остальные продолжают. Соединения распределяются по транспортам, UDP (SRT)
  делится по пакетам. Нужна нода node-v1.3.0 или новее; со старыми нодами
  профиль работает как раньше. Три документа Mail.ru, три соединения:
  0,84 МБ/с против 0,59 на одном.
- Сеть для каждого транспорта: любая, мобильная, Wi-Fi. Приложение держит мобильные данные включёнными рядом с Wi-Fi
  (разрешение CHANGE_NETWORK_STATE) и привязывает к сети сокеты транспорта.
- Главная показывает транспорты бондинга с их сетями.
- Bumps `OpenFlux` to 563ee0c (node-v1.3.0), `shared` to 204a159.

## [2.9.4] - 2026-09-30 (meepo161/OpenFluxAndroid)

### Fixed

- A node's profile (made by «Своя нода») started in exit mode ran a second
  exit on the node's own document: both exits took and answered the
  clients' packets and an iOS client got nowhere. The app now refuses to
  run it as an exit and says to switch to «Клиент» or make a separate
  profile with its own document.
- The wizard's check connects to the new node as a client whatever mode
  the app is in; it no longer asks to switch the mode first.
- Bumps `shared` to 9c5fc2f.

## [2.9.3] - 2026-09-30 (meepo161/OpenFluxAndroid)

### Fixed

- Mail.ru carrier: with both ends on the document, the connection to it
  dropped every half minute (the editor server locked the document on one
  end and dropped it when it did not unlock it), and each rejoin stalled
  traffic for up to 30 seconds; long uploads, such as a stream, broke. The
  carrier now unlocks the document for the other end. The wizard installs
  node core node-v1.2.3.
- Bumps `OpenFlux` to 14dc27d.

## [2.9.2] - 2026-09-30 (meepo161/OpenFluxAndroid)

### Fixed

- A node the wizard sets up now also serves clients that speak only the
  classic protocol, such as the iOS app: they were dropped on every carrier
  of the node and never got through. The wizard installs node core
  node-v1.2.2; nodes with the core updater on get it by themselves.
- The core no longer drops packets when a carrier's batch queue is full
  (node-v1.2.1 backpressure): it waits and retries in order.
- Bumps `OpenFlux` to d420317.

## [2.9.1] - 2026-09-29 (meepo161/OpenFluxAndroid)

### Fixed

- «Своя нода», step 4 (verification) on an Android phone in VPN mode: the app
  is kept out of its own VPN, so it could never ask where the traffic
  leaves; the step retried until its 150 s timeout and failed. It now counts
  the new node's Session answer (right key and document) as the proof, and
  Home says the exit address is seen in a browser (api.ipify.org).
- «Проверить ещё раз» after a failed verification ended at once with the old
  error: it read the previous attempt's state before the new connection
  started. It now waits for the new connection.
- A Yandex board is joined as a guest: the Yandex sign-in is no longer put
  into its carrier or offered to its node.
- Bumps `shared` to 48f1255.

## [2.9.0] - 2026-09-29 (meepo161/OpenFluxAndroid)

### Added

- «Своя нода» makes every document right in the wizard, no developer mode:
  the Yandex document (Volga), a Mail.ru document (in Cloud, published and
  open to editing by link) and a Yandex board (open to guests as editors).
  The sign-in opens in the Accounts window; a saved one skips it. A pasted
  link still works for each.
- The wizard offers two more carriers: Yandex Docs (the same Yandex document
  through the older transport) and Yandex Board. The node takes them too:
  `node-install.sh` writes `yandex` and `boards` transports, pinned at
  [`6d277eb`](https://github.com/meepo161/OpenFlux/commit/6d277ebd05ef69348b8f12ba8915c90fdcfbb19f).
- The profile editor makes a board for a Yandex Board carrier.
- Bumps `OpenFlux` to `7006309` and `shared` to `de4a638`.

## [2.8.0] - 2026-09-29 (meepo161/OpenFluxAndroid)

The fork on p1neappleXpress 2.1.0: links read and made by the core, a
classic profile as the exit node, one protocol for every client.

### Added

- Developer mode: ten taps on the app version in Settings → About show the
  «Аккаунты» tab (a switch there hides it again). In developer mode the node
  wizard also offers to sign in to Yandex and make the document, in the
  Accounts tab's sign-in window; otherwise it takes your own link.

### Fixed

- The node wizard's sign-in menus («другой способ получить код») no longer
  close as the keyboard opens: the page opens full screen in the Accounts
  tab's sign-in window instead of inside the wizard's scrolling step.

### Changed

- Bumps `OpenFlux` to the fork's [`57c69d7`](https://github.com/meepo161/OpenFlux/commit/57c69d706a71cd77f1951efeb8b438d275e1df86)
  (`v0.3.0`, node core `node-v1.2.0`) and `shared` to
  [`806f0b4`](https://github.com/meepo161/OpenFluxClientShared/commit/806f0b48de9dee598e91810f11a756f56ca578e9).

## [2.7.0] - 2026-09-28 (meepo161/OpenFluxAndroid)

### Added

- «Аккаунты»: sign in once to Yandex or Mail.ru in the built-in browser; the
  app keeps only the session (owner-only `accounts.json`, never in backups,
  logs, QR codes or links) and shows each service's status — signed in (with
  the login and when it was last checked), expired, or asking for a check.
  Sessions are rechecked every 30 minutes without the browser; «Войти заново»
  is one button, on the card and in a banner on Home when a profile needs it.
- «Создать документ» makes the channel's document with the saved account, no
  `cookies.txt` export: Yandex — a document on Disk with editing by link;
  Mail.ru — a document in Cloud (`/openflux`), published and switched to
  editing by link, then checked the way the core opens it (anonymous
  `r7/edit`). The same button sits under a document field in the profile
  editor; the node wizard uses the saved Yandex account too.
- The Yandex sign-in goes into the core's cookie store before it connects, and
  to your own node (from the wizard) over the tunnel, again after a fresh
  sign-in. Someone else's node gets it only by hand, after a warning. The
  Mail.ru account is never put into the core: its transport opens the
  document anonymously and Cloud refuses that request with the account's
  cookies.
- Cups.online: «Сгенерировать комнаты» in the profile editor opens four rooms
  without a node; the node given the same string joins them.

### Fixed

- Creating a Yandex document failed with «Failed to fetch»: Disk now
  redirects `/editnew` to `docs.yandex.ru`, where the browser refused a
  readable (CORS) request. Affected the node wizard as well.
- The built-in browser pages saw a 0 px viewport (the WebView was sized to
  its content): VK ID for Mail.ru stayed blank, and Yandex's «Resend code»
  sheet (SMS, Telegram) closed as soon as it opened. The sign-in now takes
  the whole screen on a phone.
- The core library is linked for 16 KB pages; Android 15+ no longer runs the
  app in a compatibility mode with a warning on every start.

## [2.6.0] - 2026-09-28 (meepo161/OpenFluxAndroid)

The first release of the meepo161 fork: the app, its update check, the core
submodule and the node wizard's core come from the fork's repositories
(meepo161/OpenFlux, meepo161/OpenFluxClientShared, meepo161/OpenFluxAndroid).

### Added

- «Своя нода»: step 1 asks whose core the server gets — the fork's
  (`meepo161/OpenFlux`, default) or the original (`p1neappleXpress/OpenFlux`);
  the node's auto-update then follows that repository.
- The core is built into the APK (fork `v0.2.0`); the choice of the
  node's core is in the wizard.

### From p1neappleXpress/OpenFluxAndroid (not yet released there)

### Added

- Developer mode (ten taps on the app version in Settings → About) shows
  the «Аккаунты» tab: sign in once to Yandex or Mail.ru in a WebView; the
  app keeps only the session (owner-only `accounts.json`, never in backups,
  logs, QR codes or links) and shows each service's status — signed in,
  expired, or asking for a check. Sessions are rechecked every 30 minutes;
  «Войти заново» is one button.
- «Создать документ» makes the channel's document with the saved account:
  Yandex — a document on Disk with editing by link; Mail.ru — a document in
  Cloud (`/openflux`), published and switched to editing by link. In
  developer mode the node wizard offers it too, in the same sign-in window.
- The Yandex sign-in goes into the core's cookie store before it connects,
  and to your own node (from the wizard) over the tunnel. The Mail.ru account
  is never put into the core: its transport opens the document anonymously.
- Cups.online: «Сгенерировать комнаты» in the profile editor opens four
  rooms without a node.
## [2.4.1] - 2026-10-11 (p1neappleXpress/OpenFluxAndroid)

The core 0.5.1.

### Fixed

- **Telemost no longer joins the room as «OpenFlux».** Lanes join as «Гость» with a browser-like
  RTCP CNAME, so the participant name no longer gives the tunnel away (core #169).
- **Mail.ru Docs:** `Accept-Language` now rides on the WebSocket upgrade and the API calls, for
  clients that still saw 409 after the earlier User-Agent fix (core #164).
- **Telemost stability:** an exit no longer crashes when a peer leaves mid-transfer, and downloads
  through an L4 exit are no longer capped near 1.5 MB/s (core 0.5.1).

## [2.4.0] - 2026-10-11 (p1neappleXpress/OpenFluxAndroid)

The core 0.5.0: the Telemost transport.

### Added

- **Yandex Telemost as a transport (experimental).** A profile of the type «Яндекс Телемост» needs only the
  link of a Telemost conference that the exit node joins too; the tunnel rides the conference as video,
  striped over five participants on each end (core 0.5.0, `OpenFlux` bumped to
  [`7336e1b`](https://github.com/p1neappleXpress/OpenFlux/commit/7336e1b)). The exit is the 0.5.0 core
  (`node-v1.3.0`) started with `-role exit -t telemost -u <conference link>`; its `--share` link imports as
  a profile and runs through the VPN. Measured on a phone through a server exit: about 0.8-1.5 MB/s on
  average; the first seconds of every transfer are slower while the conference ramps up. One conference
  serves one client.

### Changed

- **The tunnel's TCP rides out a late carrier.** Carriers deliver in order but now and then late; the
  tunnel's TCP now waits that out (a 3-second retransmission floor, loss detection by duplicate ACKs)
  instead of shrinking its window to a crawl (core 0.5.0).

## [2.3.3] - 2026-10-11 (p1neappleXpress/OpenFluxAndroid)

Hotfix: the core 0.4.3 — the Yandex and Mail.ru carriers.

### Fixed

- **Yandex: the carrier relays tunnel packets again.** The outgoing cursor message declared a fixed
  length of `18` before its payload, but Yandex Disk does not relay a cursor whose declared length does
  not match the packet, so every tunnel packet was dropped and the Session handshake timed out. The real
  packet length is sent now (core 0.4.3, `OpenFlux` bumped to
  [`d7105bd`](https://github.com/p1neappleXpress/OpenFlux/commit/d7105bd)). Verified end to end from this
  app: a real HTTPS request over the Yandex carrier reaches the exit. Reported by @Gamazzz
  ([OpenFlux#165](https://github.com/p1neappleXpress/OpenFlux/issues/165)) — thank you.
- **Mail.ru Docs: the carrier comes up on public links again.** Mail.ru's nginx answered `409 Conflict`
  to the WebSocket upgrade, so the transport never connected; it now offers `permessage-deflate` and uses
  a current Firefox `User-Agent`. Reported by @pirsasha
  ([OpenFlux#163](https://github.com/p1neappleXpress/OpenFlux/issues/163)) and @Shiller70
  ([OpenFlux#164](https://github.com/p1neappleXpress/OpenFlux/issues/164)) — thank you. Exit nodes on a
  VDS need the same core: «Своя нода» installs `node-v1.2.3`.

## [2.3.2] - 2026-10-07

Hotfix: the core 0.4.2.

### Fixed

- **Mail.ru Docs: a link that only lets the document be read no longer drops the connection every few
  seconds.** The transport sent the editor's `saveChanges` now and then, and for a link without edit rights
  (what most public links are) the server closed the connection on every one, so the channel was up for
  moments between reconnects. It is not sent there any more (core 0.4.2, `OpenFlux` bumped to
  [`74cac6d`](https://github.com/p1neappleXpress/OpenFlux/commit/74cac6d)). The Engine.IO / Socket.IO
  handshake is also completed before the auth is sent (thanks to Novarg93,
  [OpenFlux#147](https://github.com/p1neappleXpress/OpenFlux/pull/147)). Exit nodes on a VDS need the same
  core: «Своя нода» installs `node-v1.2.2`.

## [2.3.1] - 2026-10-07

Hotfix: the core 0.4.1.

### Fixed

- **Yandex Docs / Boards / Volga through a node: the check no longer repeats forever.** The check the
  node asks for is passed in the app's browser from the node's IPv4 address (through the tunnel), but
  the node's own requests went out over IPv6 on hosts that have an IPv6 route, so Yandex never accepted
  the cookies the app handed over. The core now dials Yandex IPv4 first (core 0.4.1, `OpenFlux` bumped to
  [`d0578b6`](https://github.com/p1neappleXpress/OpenFlux/commit/d0578b6)). Exit nodes on a VDS need the
  same core: «Своя нода» installs `node-v1.2.1`.
- **Exit node, L3: the log (`-d`) shows the tunnel's packets only**, not every packet the host receives.

## [2.3.0] - 2026-10-07

### Added

- **«Экспериментальные функции»** (Settings): the JS-transport features below are off by default and
  for settings saved before; nothing of them is shown, started or fetched, and the JS engine is not
  started when the app starts, until the switch is on.
- **JS transports** (experimental, off by default): the «Транспорты» tab, installing a signed
  transport by link, file or catalog with a trust dialog and the author's key fingerprint, the
  settings a transport declares (a wizard page), its own setup and login pages in a WebView, update
  check with changelog, apply and roll back, and the transports shipped in the app (installed, or
  replaced by a newer shipped version, the first time the switch is turned on). A profile that uses
  a JS transport does not connect while the switch is off, and says so.
- A second release channel: nightly test builds next to the main releases. A
  nightly is a GitHub prerelease tagged `nightly-<date>-<commit>`, built by the
  new `Nightly` workflow from the `nightly` branch (on a push to it, and every
  night when it has a commit without a build), signed with the same key as a
  main release so it installs over one; the newest 7 are kept. Settings → About
  has a «Ночные сборки» switch: with it on, «Проверить обновления» also looks
  at nightlies; the main channel never sees them.

### Changed

- Bumps `OpenFlux` to the core 0.4.0 ([`a415c09`](https://github.com/p1neappleXpress/OpenFlux/commit/a415c09d1d1006a201b5e9523d2f30d7ee2b4149)) and `shared` to
  [`a7fca72`](https://github.com/p1neappleXpress/OpenFluxClientShared/commit/a7fca72).
- The core's Yandex check on a profile without a key now reaches the app (a profile without a Session
  used to log "external solver required" and never ask for the check).

## [2.2.0] - 2026-10-01

### Fixed

- «Без сервера»: the node now stays up on any host and keeps the tunnel going
  across its generations, and the install no longer fails when the host cuts a
  transfer short (InfinityFree's FTP aborted the link parser part way): a file is
  sent again, and `auto` goes on in plain FTP after it keeps failing over TLS.
  The wizard also adds a node already on the hosting from its site and access key
  (no FTP), takes a key of your own on an FTP install, opens the node's panel in
  the browser, and shows which generation serves («работает · поколение N»).
  Bumps `OpenFlux` to
  [`bb55dc3`](https://github.com/p1neappleXpress/OpenFlux/commit/bb55dc3) and
  `shared` to
  [`98572e2`](https://github.com/p1neappleXpress/OpenFluxClientShared/commit/98572e2).
- The core's Yandex Boards moves packets as `modify-objects` text objects (the
  board no longer relays `notify-position`), and Yandex Docs and Mail.ru Docs
  send the web client's `saveChanges` stream built from the live session.

### Added

- With carriers at equal top priority the badge and «Сейчас через» name all of
  them ("Board + Volga 2"), as the core spreads traffic over the whole group.

- «Без сервера»: a new profile mode where the exit is a small PHP program on an
  ordinary web hosting (free or paid, any with PHP and FTP/FTPS) instead of your
  own server. The wizard (Профили → «Без сервера») takes the FTP data and the
  site's address, creates a cups.online room (or takes a Mail.ru document),
  uploads the node, checks that the site runs it, starts it and connects
  through it before saving the profile. Works as the system-wide VPN or as a
  local SOCKS5 proxy; no key to keep, TCP on ports 80 and 443 only. The node
  renews itself while it is used, and connecting a profile made this way first
  asks its node to run when the hosting is reachable. Its `openflux://` link and
  QR carry the mode (never the node's token).

- «Своя нода»: a new channel is no longer Yandex-only. Step 2 picks any mix
  of a Yandex document (your own link), a Mail.ru public document and
  cups.online rooms (created automatically), with direct always on as the
  backup; the link and the saved profile carry all of them.
- «Автообновление ядра» on the plan step (on by default): the server's
  `openflux-node-update.timer` checks the newest `node-v*` release every
  6 hours, verifies it against the release's `node-install.sh` and
  `SHA256SUMS`, restarts the channels and rolls back if one does not stay
  up.
- Bumps `OpenFlux` to [`ee7cf56`](https://github.com/p1neappleXpress/OpenFlux/commit/ee7cf56d27549018d5fc3f6a5a31445fff55380c)
  and `shared` to [`b23354a`](https://github.com/p1neappleXpress/OpenFluxClientShared/commit/b23354ab2ecf8968eca8a3d1b7b66f6f74be07aa).

## [2.1.0] - 2026-09-28

### Changed

- Share links are read and made by the core, the way every client does:
  bumps `OpenFlux` to [`2ec01a5`](https://github.com/p1neappleXpress/OpenFlux/commit/2ec01a5) (core 0.2.0) and `shared` to
  [`ae5e59a`](https://github.com/p1neappleXpress/OpenFluxClientShared/commit/ae5e59a).
  - A link that picked up line breaks, spaces, non-breaking or zero-width
    characters, padding or the standard base64 alphabet on the way imports,
    as on iOS, instead of «Ссылка повреждена».
  - The same profile makes the same link on Desktop, Android and iOS; the
    core names the encryption context of a Session link, the app no longer
    derives it.
  - A refused link says why: not a link, cut short, letters changed case,
    unknown transport, key too short, and so on.
  - The node wizard installs `node-v1.1.0`, the node build of core 0.2.0.
- The node wizard's document step takes the link of a document you
  created; the button that signed in to Yandex and created one is gone.
- The release notes show this changelog.

### Fixed

- Clients and nodes built from different trees now connect: bumps `OpenFlux`
  to [`f8f3476`](https://github.com/p1neappleXpress/OpenFlux/commit/f8f34767a5732febd5965ac6cf95bad51b70cf99).
  - A classic profile with a key runs the Session and falls back to the
    classic layering for a classic or older node, on the same carrier; a
    node set up as classic serves both kinds of client. Nodes set up for
    the Session stay Session-only.
  - The encryption context follows one rule everywhere, and a client whose
    context differs from the node's finds the node's instead of timing out
    (classic cups.online was the common case).
  - The classic codec (batched or legacy) is no longer a hard requirement:
    both are accepted and the client switches when the node does not answer.
  - boards no longer drops the connection every 20 seconds; yandex and
    mailru reconnect when their socket dies.
  - The log explains a failed handshake: wrong key, a node in the other
    mode, the codec or context picked, a connection taken over by another
    client.

## [2.0.1] - 2026-09-27

### Fixed

- An exit node deployed by the node wizard never actually connected: a
  `.conf`-only `Role = exit` (every node-wizard deployment) left the core's
  internal exit/client flag stuck at its pre-config value, so the exit
  never answered the handshake and crash-looped instead. Bumps `OpenFlux`
  to [`e8f735a`](https://github.com/p1neappleXpress/OpenFlux/commit/e8f735a98c1ba9e091956416fde5c5ef92d3cd66).
- The startup log always printed `Transport: yandex` for session/multi-transport
  profiles regardless of which transports were actually configured (a stale
  flag default, not a functional bug — the correct transports ran either
  way). Now prints the actual list, e.g. `Transport: boards, direct (session)`.
- Cups.online profiles with no room codes couldn't be saved or connected;
  the node generates its own rooms, so an empty value is valid for this
  transport only.
- An exit node always listened for Direct (TCP on `0.0.0.0:<port>`) and put
  it into the clients' link, even when the profile had no Direct transport.
  It now listens only when the profile has Direct, at that transport's
  priority ([#11](https://github.com/p1neappleXpress/OpenFluxAndroid/pull/11)).
- A phone exit on cups.online without room codes put cups.online into its
  link with no rooms, so clients could not join; the link now carries the
  rooms the node created ([OpenFlux#123](https://github.com/p1neappleXpress/OpenFlux/pull/123)).

### Added

- Settings → Ядро OpenFlux: a core log-level picker (Выкл / -d / -dd /
  -ddd, the core's `--debug=N`) in place of the "Подробный журнал ядра"
  switch. The embedded core used to run at -dd on every connect whatever
  the switch said — formatting a log line for every packet — and the
  switch only hid those lines from the log view; the level now reaches the
  core itself ([OpenFlux#121](https://github.com/p1neappleXpress/OpenFlux/pull/121)).
  The default is Выкл: choose -dd to see transport errors, handshakes and
  the encryption (KDF) context.
- Node-wizard deployment logging: every SSH/RPC call and the wizard's own
  step narration now goes to the Logs tab, so a stuck deployment is
  diagnosable without a debugger.
- Home → Подключение: a "Сейчас через" row for profiles with several
  transports, and carriers named as in the app ("Board 2", not `boards-2`)
  there and in the "через …" badge.

## [2.0.0] - 2026-09-27

First release of this app. Replaces the previous single-transport native
app (tun2socks + pdnsd JNI, preserved at the
[`legacy-native-app`](../../tree/legacy-native-app) tag) with the Compose
Multiplatform app originally built by [@meepo161](https://github.com/meepo161)
in [OpenFluxClient](https://github.com/meepo161/OpenFluxClient), moved here
with his agreement.

### Added

- System VPN or local SOCKS5.
- Multi-transport sessions with automatic failover and priority-based
  switching (including `direct`).
- AES-256-GCM session encryption.
- SmartCaptcha and login handling in a built-in browser, including a
  transport check on the exit node, passed through the tunnel from its
  own address.
- A node-deployment wizard: install an exit node on your own VPS over SSH
  from the app.
- `shared/` and `OpenFlux/` as git submodules ([OpenFluxClientShared](https://github.com/p1neappleXpress/OpenFluxClientShared)
  and the [OpenFlux](https://github.com/p1neappleXpress/OpenFlux) core), so
  this app always builds against one pinned, single copy of each instead of
  a vendored one.
- `.github/workflows/release.yml`: a `v*` tag builds and signs a release
  APK and publishes it here.

### Credits

- [@meepo161](https://github.com/meepo161) — this app's UI and logic.
- [@p1neappleXpress](https://github.com/p1neappleXpress) — the OpenFlux
  core it embeds.
- [@damnurmum](https://github.com/damnurmum) — `openflux://` links/QR codes
  and the cups.online transport in the core, which this app's share and
  scan screens build on.
