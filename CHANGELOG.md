# Changelog

## 0.6.5-preview — 2026-10-20

- Одна плитка на точный пакет приложения в каталоге, библиотеке и обновлениях. Внизу открытой карточки — выбор релиза с версией, размером и полным именем. Старые и равные по коду версии остаются доступны; поиск учитывает все релизы. Установка и скачивание используют именно выбранный релиз.
- Исправлены обложки и фотографии: свежая обложка и галерея запрашиваются со страницы Meta вместо истёкших подписанных ссылок. Готовая публичная галерея больше не ждёт обхода зеркала; зеркало остаётся запасным источником.
- После возврата карточки в сетку отменённая загрузка обложки возобновляется; ошибки повторяются ограниченное число раз. Неудачный кадр можно загрузить повторным выбором. Медиа не сохраняются на диск.
- One tile per exact app package; choose the install release at the bottom of the open card. Preserves older/equal-code variants and release-aware search; actions use the selected release.
- Refreshes expired cover URLs from the current Meta page, avoids blocking a ready gallery behind mirror scans, restarts recycled-cell cover requests and allows failed-frame retries. Bounded RAM-only media caches.
- Validation: 172 isolated Quest UI/cache checks (including selected action targets and cover cancellation/reattachment), 79 isolated live media/storage checks with real cover, screenshots and video; 15 catalogue checks and existing build suites; 16 installed-app checks with the real catalogue/versions and preserved queue. No catalogue-wide network scan.


## 0.6.4-preview — 2026-10-20

- По просьбе пользователя отменено последнее изменение 0.6.3. Счётчик фотографий убран, интерфейс карточки возвращён к 0.6.2: надпись «Загрузка» и три точки.
- Reverts the 0.6.3 photo counter at the user's request; restores the 0.6.2 loading label and three-dot interface.
- Проверки не запускались по просьбе пользователя. / Tests not run at the user's request.


## 0.6.2-preview — 2026-10-20

- Полупрозрачная надпись «Загрузка» и три переливающиеся точки по центру изображения при открытии карточки. Индикатор ждёт завершения получения списка и всех попыток предзагрузки фотографий в RAM, включая ошибки, но не видео.
- Анимация не перехватывает нажатия, останавливается при закрытии карточки и скрытии окна. Загрузка отдельных фото не снимает индикатор раньше завершения всей очереди.
- Translucent loading label and three pulsing dots overlay game-card images until photo discovery and all RAM prefetch attempts finish. Hidden for video; animation stops on close/window hiding and does not capture input.
- Проверки не запускались по просьбе пользователя. / Tests not run at the user's request.


## 0.6.1-preview — 2026-10-20

- Компьютеры в сетевом выборе APK показаны адаптивной сеткой плиток. Имя крупно сверху, IP ниже; при отсутствии сетевого имени показан только IP.
- Network computer picker uses responsive tiles with the resolved name above the IP address; unknown names fall back to the IP alone.
- Проверки не запускались по просьбе пользователя. / Tests not run at the user's request.


## 0.6.0-preview — 2026-10-20

- Кнопка «APK с компьютера»: поиск локальных компьютеров, вход в SMB2/SMB3, список общих папок, переход по вложенным папкам и выбор APK. Доступ на чтение; пароли только в памяти соединения.
- Передача с прогрессом/отменой, проверка реального APK и обычная системная установка через очередь QCatalog. Пароли и списки папок не сохраняются. Незавершённый сетевой импорт выбирается заново; докачка QLoader остаётся отдельной функцией.
- Отдельные отклонения Touch-стика снова переключают кадр сразу. Односекундный интервал применяется к непрерывному удержанию; конец scroll-потока распознаётся по паузе между событиями.
- Network APK picker: local discovery, SMB2/3 authentication, share/folder browsing, cancellable copying and normal Android installer handoff. Read-only access; memory-only credentials.
- Separate stick flicks act immediately; continuous holding repeats once per second. Added multi-DEX packaging and pinned/licensed SMB dependencies.
- Validation: 153 isolated UI/cache checks, 16 host address/path checks and existing build suites; 16 isolated Quest SMB2/picker/APK checks against a temporary read-only share, including exact SHA-256, cancellation, password cleanup, APK import and real installer-session staging. Test installation session was abandoned; no user game was installed or changed. Temporary share/server, folder and credentials removed afterwards.

## 0.5.6-preview — 2026-10-20

- При открытии карточки её изображения заранее загружаются и декодируются в RAM. Листание готовых кадров использует те же объекты без повторных сетевых запросов.
- Кэш принадлежит только открытой карточке: до 17 изображений, два фоновых запроса, максимум 64 МиБ или четверть Java-кучи. Размер изображений ограничивается бюджетом памяти.
- При закрытии отменяются запросы и освобождаются изображения, плеер и RAM-буфер видео. Поздние результаты сразу освобождаются; медиафайлы на диск не записываются. Видео запускается пользователем.
- Game-card images preload/decode into a bounded, card-scoped RAM cache. Ready-frame navigation reuses decoded bitmaps; close cancels sources, frees pixels/video buffers and discards late results. No disk media cache.
- Validation: 151 isolated UI/cache checks on Quest 3S, including full prefetch, reuse, memory bounds, file-list comparison and close/cancellation; existing build suites passed. 76 storage/queue/media checks additionally verified a real gallery prefetch, video and no new media files.

## 0.5.5-preview — 2026-10-20

- Исправлена причина отсутствия реакции Touch-стиков: Horizon OS передаёт горизонтальный стик в 2D-окно как ACTION_SCROLL / AXIS_HSCROLL, а не как обычный геймпад. Добавлена обработка этих событий.
- Листание работает также в карточке игры, когда указатель находится над медиа. При удержании кадры меняются раз в секунду; завершение потока прокрутки не оставляет работающий таймер.
- Ссылки и email в описании кликабельны. Адреса именованных HTML-ссылок сохраняются при очистке текста; замена оригинала переводом обновляет ссылки.
- Handles Horizon OS pointer-scroll events from Touch thumbsticks in expanded media and hovered game-card media, throttled to one frame per second while scrolling continues. No timer continues after scroll pulses stop.
- Clickable web/email links in descriptions; named HTML destinations survive text cleanup and links refresh when translation replaces the source.
- Validation: 88 isolated UI checks on Quest 3S, including scroll-event routing in both layouts, repeat/cancellation, link destinations and browser intents; 11 offline description checks and existing build suites passed. The headset owner confirmed physical Touch-stick navigation in both layouts.

## 0.5.4-preview — 2026-10-20

- В увеличенном просмотре карусель переключается стиком влево/вправо. Первый кадр меняется сразу; удержание повторяет переключение раз в секунду.
- Поддержаны аналоговые оси стиков и события D-pad; небольшие отклонения и преимущественно вертикальные движения не листают карусель. Возврат в центр, потеря фокуса, закрытие просмотра и уход приложения в фон отменяют повтор.
- Expanded carousel accepts horizontal stick input: immediate first step, then one step per second while held. Neutral input, focus loss, collapse and backgrounding cancel repetition.
- Validation: 72 isolated UI checks on Quest 3S, including injected controller events and actual delayed repetition. Physical Touch-controller event delivery remains to be confirmed by the headset owner. Existing build suites passed.

## 0.5.3-preview — 2026-10-20

- Убраны счётчик кадров, подписи карусели, кнопка оригинала описания и служебные сообщения о переводе.
- Английский оригинал показывается сразу после загрузки; русский перевод заменяет его при успехе. При сбое или пустом переводе остаётся английский.
- Removed carousel counters/captions, the original-description button and translation-status messages. English appears immediately and is replaced by Russian when available; failures retain English.
- RU/EN README documents the sampled catalogue languages and the limits of hosted translation options. MyMemory remains the keyless provider; no claim of unlimited service or best translation quality.
- Validation: 58 isolated UI checks on Quest 3S; 9 offline description formatting/language-selection checks; existing build suites passed.

## 0.5.2-preview — 2026-10-20

- Клик по изображению или кадру видео увеличивает его; повторный клик возвращает. Убраны отдельные кнопки увеличения, закрытия и подложка просмотра.
- Исправлено увеличение самого видео с сохранением позиции воспроизведения. Прозрачные края вместо залитых боковых панелей, пропорции кадра сохраняются.
- Нижняя панель видео: запуск/пауза, время, перемотка, звук и громкость плеера. Карусель использует полупрозрачные стрелки без фона.
- Click an image or video picture to expand it, click again to restore. No separate enlarge/close buttons or viewing backdrop; playback position survives resizing.
- Transparent video edges and carousel chevrons; bottom play/pause, time, seeking, mute and player-volume controls.
- Validation: 67 isolated UI checks on Quest 3S, including a real public video and playback continuity across expansion/restoration.

## 0.5.1-preview — 2026-10-20

- Узкое окно игры: максимум 720 dp вместо 90% ширины панели.
- Стрелки карусели по бокам кадра, на всю его высоту; появляются при наведении и скрываются после ухода указателя. Доступен также клавиатурный фокус.
- README на русском и английском приведён к текущему интерфейсу и кнопкам, новые возможности перенесены из разрозненных дополнений в основные разделы.
- Narrow game-details dialog, full-height edge arrows revealed on hover/focus, and a reorganized current Russian/English README.
- Validation: 45 isolated UI checks on Quest 3S, including pointer enter/exit and arrow geometry.

## 0.5.0-preview — 2026-10-20

- Версия конкретного релиза и код на карточках каталога, библиотеки и обновлений.
- Крупная карусель обложки, скриншотов и видео в одном блоке, стрелки, жесты и увеличение. Видео по кнопке с паузой и перемоткой.
- Свежие ссылки на скриншоты из публичной страницы Meta по ID QLoader; трейлеры также читаются с зеркала QLoader. Ограниченный буфер в RAM, без файлов медиа и дискового кэша.
- Per-release versions on game cards; a large cover/screenshot/video carousel with arrows, swipe, image enlargement and user-started playback with pause/seeking.
- Fresh public store media scoped to the QLoader game ID, mirror trailers, RAM-only loading and cleanup on close.

## 0.4.3-preview — 2026-10-20

- Поддержаны install.txt с одним APK и размещением данных adb push. Встроенная распаковка 7z с CRC-проверкой, ограничением путей, места и памяти, повторным запуском после прерывания.
- Half-Life / XASH распаковывается и копируется прямо на шлеме. Существующие файлы, которых нет в поставке, включая сохранения, не удаляются; команды uninstall/rm из текста не запускаются.
- Старая ошибка install.txt переводится в готовую к установке задачу без повторной загрузки. Неизвестные инструкции по-прежнему явно отклоняются с сохранением файлов.
- Declarative install.txt support for one APK and adb push data placement, with built-in checked 7z extraction. No shell execution, uninstall or recursive deletion of installed game data.
- Legacy install.txt errors can retry installation from existing downloads. Unsupported instructions remain explicit errors.

## 0.4.2-preview — 2026-10-20

- Компактные карточки: обложки примерно в 1,5 раза ниже, меньше отступы, до пяти колонок; кнопки загрузок сохраняют размер.
- Зелёная галочка «Есть обновление» в библиотеке по сравнению числовых версий. Текущая или более новая установленная версия не отмечается.
- Compact cards, smaller covers and spacing, up to five columns. Download controls retain their size.
- Green library update check based on numeric installed/catalogue versions; no check for current or newer installed versions.

## 0.4.1-preview — 2026-10-20

- Выровнены кнопки «Пауза», «Установить» и «Подробнее»: единый отступ ряда, одинаковые верхняя/нижняя границы; перенос в колонку при нехватке места и крупном шрифте.
- Развёрнутая горизонтальная панель 1920 × 1080 dp при запуске, без свободного сжатия окна. Содержимое занимает всю выделенную область. Положение/угловой размер панели остаются под управлением Horizon OS.
- Светлая и тёмная темы автоматически по системному uiMode: фон, поверхности, текст, действия, иконки, диалоги и индикаторы; системная принудительная инверсия отключена. Поиск и выбранный раздел сохраняются при пересоздании окна.
- Fixed action alignment, fully expanded landscape launch and automatic system light/dark theme.
- Validation: 22 isolated UI checks, including real 1920 × 1080 dp launch, matching action bounds, both themes and 160% font scale; existing host suites pass. No global headset settings changed.
## 0.4.0-preview — 2026-10-20

- Реализованы #1 и #2 вместе: утверждённые Android-ресурсы Cards v1, светлая тема, боковое меню, адаптивная сетка и крупные действия.
- Сортировки А–Я и по реальной популярности скачиваний QLoader за сутки/7/30 дней с честным состоянием ошибки и отсутствующих данных.
- Структурированный прогресс скачивания, подготовки APK, копирования и SHA-256 проверки. Системная установка без ложного процента и кнопки паузы.
- После проверенной новой установки/обновления автоматически очищается только её папка установочных файлов, завершённая задача исчезает из активных загрузок. Версии перечитываются из Android.
- Ошибка установки сохраняет источники; ошибка очистки предлагает повтор только очистки. Восстановление прерванного копирования сохраняет загрузку.
- Обложки только видимых карточек, ограниченный RAM-кеш 16 МиБ. Описания/переводы/обложки не сохраняются на диск.
- Implements approved Cards v1 UI and phase progress/verified staging cleanup together. Includes QLoader popularity sorting, bounded memory-only visible covers and a cleanup-only retry.
- Validation: 14 host copy/progress/verification checks, 24 isolated Android checks, 10 isolated UI checks on Quest, plus existing host parser/cleanup/description/signing/resume suites. UI fixture renders cover narrow and wide layouts; subjective VR/controller ergonomics remain for the headset owner to assess.
## 0.3.0-preview — 2026-10-19

- Продолжение файлов по сохранённому байту, устойчивые checkpoints и автоматическое восстановление очереди после запуска приложения/шлема.
- Ручная пауза сохраняется; установка данных больше не пропускается из-за уже обработанной загрузки того же релиза.
- Durable byte-offset resume and automatic queue recovery after app/headset startup.
- Manual pauses remain paused; data installation is no longer skipped for an already attempted download.

- Проверка явно несовместимых подписей APK до установки с учётом истории ротации сертификатов.
- Понятные причины отказа Android, включая конфликт подписи и нехватку места.
- Отдельный статус «Ошибка установки» и повторная установка без повторного скачивания.
- Conservative signing-certificate preflight, including rotation history.
- Actionable Android installer errors, including signing conflicts and storage failures.
- Separate installation-error status and retries without downloading again.

Validation: 12 host checkpoint/resume checks and 13 installation checks pass. Real conflicting/matching APK certificates were checked on Quest. The final isolated Android run passed 18 checks, including native range resume, SHA-256 validation, production downloader integration on external storage, queue recovery and simulated boot handling. The headset itself was not rebooted during an active user download.

## 0.2.1-preview — 2026-10-19

Первый опубликованный preview-релиз / First published preview release.

- Каталог, поиск, загрузки и установка обычного APK прямо на Quest.
- Удаление файлов и очистка истории отдельными действиями.
- Установленные версии и доступные обновления по Android versionCode.
- Описания на русском и обложки по запросу, без файлового кэша.
- Исправлена пустая сохранённая ссылка при подключении через файл конфигурации.
- Инструкции RU/EN, помощник переноса подключения, сборка и тесты.

- Browse, search, download and install ordinary APK releases directly on Quest.
- Separate file deletion and history cleanup.
- Installed-version detection and update suggestions using Android versionCode.
- On-demand Russian descriptions and covers with no disk cache.
- Fix empty stored URLs when connecting through a configuration file.
- RU/EN setup guide, provisioning helper, build and test scripts.

Validation: host catalogue, cleanup and formatting tests pass. Thirteen on-device checks of cleanup/version logic passed on 0.2.0-preview; six isolated on-device storage checks passed for the 0.2.1 fix. One ordinary APK installation was previously verified on Quest 3S. Live card rendering, OBB installation and compatibility with the entire catalogue remain unverified. See README for details.
