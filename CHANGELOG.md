# Changelog

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
