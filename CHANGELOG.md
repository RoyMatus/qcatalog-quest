# Changelog

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
