# QCatalog for Meta Quest

Каталог QLoader прямо на шлеме: поиск, скачивание, установка APK и проверка обновлений.

**[Скачать APK / Download APK](https://github.com/RoyMatus/qcatalog-quest/releases/tag/v0.5.0-preview)** · [Issues](https://github.com/RoyMatus/qcatalog-quest/issues) · [Русский](#русский) · [English](#english)

Версия / Version: **0.5.0-preview**. Проверено / Tested: **Quest 3S, ARM64, Android API 34**, 20 October 2026.


### Интерфейс и сортировка / Interface and sorting

Используется утверждённый [набор Cards v1](design/cards-v1/README.md), импортированный в `res/` без подмены цветов и иконок. В узком окне разделы открываются кнопкой «Разделы»; в широком видны боковое меню и сетка. С версии 0.4.1 окно сразу открывается развёрнутым в горизонтальном формате 1920 × 1080 dp; ручное растягивание не требуется. Фоновый цвет, кнопки и диалоги автоматически следуют системной светлой/тёмной теме. Ряд действий имеет одинаковые границы кнопок и при необходимости переносится в колонку. Настройки подключения доступны через «Разделы → Настройки» либо нижнюю кнопку бокового меню.

Сортировка рядом с поиском: А–Я или популярность скачиваний за сутки / 7 / 30 дней. Источник — [публичная статистика QLoader](https://qloader.5698452.xyz/api/v1/popularity), а не рейтинг магазина Meta или точное число всех скачиваний. Выбранный режим сохраняется; сами данные статистики — только в RAM. Никакая личная история скачиваний этому сервису не передаётся. Повторный выбор сортировки позволяет повторить запрос после ошибки. Обложки видимых карточек используют ограниченный кеш RAM 16 МиБ, описания по-прежнему загружаются только по запросу; изображения/описания не сохраняются на диск.

Approved [Cards v1 resources](design/cards-v1/README.md) are imported into `res/`. Narrow windows use the labelled Sections menu; wider windows show the sidebar and a compact grid of up to five columns. From 0.4.1 the panel opens fully expanded at 1920 × 1080 dp in fixed landscape format, without manual stretching. System light/dark mode controls surfaces, text, buttons and dialogs. Action buttons share equal bounds and stack when large text cannot fit. Connection settings are under Sections → Settings or the sidebar Settings button.

Sorting beside search supports A–Z and QLoader download popularity for 1/7/30 days, using its public API. This is not Meta store star ratings or an exact global download total. Missing statistics sort last; service failures explicitly fall back to alphabetical order. Only the selected sort setting persists; the data stays in RAM. No personal download reports are sent. Visible covers use a bounded 16 MiB RAM cache, with no disk media/description cache.

Progress is phase-specific: downloading, APK preparation, Android installation (indeterminate when its exact percentage is unavailable), copying data and SHA-256 verification. After a newly successful install/update, only that task's downloaded staging files are removed. Installed OBB files, saved games, mods and other tasks are preserved. Cleanup failures expose a cleanup-only retry; installation/data failures keep the sources. Old completed entries remain available for manual cleanup and are hidden from active downloads.

Validation for 0.4: 14 host copy/progress/corruption checks; 24 isolated Android checks for progress, version confirmation, cleanup boundaries, retry, sorting and a live popularity request from Quest; 10 isolated UI checks with synthetic data and no Internet permission, including narrow/wide render snapshots. Existing host parser/signing/resume tests also pass. Device UI render checks do not establish subjective comfort or controller-ray usability: the owner should verify those in the headset.

## Русский

С версии 0.4.2 карточки компактнее: обложка 100 dp вместо 152 dp, меньше отступы; 5 колонок при 1920 dp и 4 при 1280 dp. В библиотеке зелёная галочка «✓ Есть обновление» появляется только для более новой версии из каталога.


### Что это

QCatalog — отдельное Android-приложение с обычным оконным интерфейсом для Quest. После первоначальной установки и подключения шлем сам получает каталог, скачивает релизы и вызывает системный установщик. Компьютер для этих действий больше не нужен.

Приложение не привязано к одному человеку: другой пользователь устанавливает тот же APK и подключает **свой** совместимый конфиг QLoader. Логин и пароль внутри QCatalog не вводятся. Используется HTTPS-ссылка на конфигурацию либо файл `FFA_config`. Конфиги, аккаунты, игры и персональные ссылки в APK и этом репозитории отсутствуют.

Это самостоятельный экспериментальный клиент, не официальный продукт Meta, QLoader или dipVR. Проверено подключение к конфигурации дистрибутива QLoader 1.2.0 от dipVR / VR Games RUS. Совместимость с другими поставщиками не подтверждена.

### Возможности

- Поиск по названию, релизу и идентификатору приложения.
- Загрузка на сам шлем, очередь, пауза и повторная попытка.
- Восстановление очереди после запуска приложения или шлема и продолжение частично скачанного файла с сохранённой позиции.
- Установка поддерживаемых APK с подтверждением в системном окне Android.
- Светлый интерфейс «Карточки»: каталог, библиотека, загрузки и обновления. Крупные цели нажатия и сетка 3/2/1 колонки по ширине окна.
- Сортировка А–Я и по популярности скачиваний QLoader за сутки, 7 и 30 дней. Нет статистики — игра в конце; при ошибке сервиса явно показывается А–Я.
- Прогресс скачивания, подготовки APK, копирования и проверки данных. Системная установка Android без точного процента показана неопределённым индикатором.
- Сравнение установленного `versionCode` с числовым кодом версии в каталоге; кнопка «Обновить» для более нового релиза.
- Удаление скачанных файлов отдельно от истории; очистка одной записи или всей истории.
- Краткое описание на русском и обложка при открытии карточки. Описания, переводы и изображения не записываются в файлы на шлеме.

### 1. Получить свой конфиг через материалы в Telegram

Проверенные ссылки поставщика:

- Канал [dipVR](https://t.me/dipVR).
- [Пост с QLoader и инструкциями](https://t.me/dipVR/178).
- Группа [VR Games RUS](https://t.me/VRGamesRUS) с вопросами и поддержкой.
- [Страница Windows-сборки без трейлеров](https://cloud.dipvr.ru/download/no-trailers/), на которую ведёт ссылка из поста.

Публичный пост ведёт к дистрибутиву QLoader. Отдельный Telegram-бот или команда для непосредственной выдачи конфига не подтверждены. Ниже описан проверенный путь через установленный QLoader; если у поставщика изменится порядок доступа, актуальные условия нужно уточнить в его группе.

1. Открой пост в Telegram и перейди по ссылке на QLoader для своей системы. Для Windows достаточно варианта без трейлеров.
2. Установи и запусти QLoader на компьютере по инструкции поставщика. Дождись, пока в нём откроется каталог. QCatalog сам не создаёт доступ к серверам.
3. Найди каталог данных QLoader. Для Windows в инструкции указан `%LOCALAPPDATA%\QLoader`; в переносной установке файлы могут лежать рядом с `Loader.exe`. Точный путь QLoader печатает в начале своего журнала как `Data directory`.
4. В этой папке найди `overrides.conf` и строку `ConfigUpdateUrl=...`. Полное значение после `=` — ссылка подключения для QCatalog. Передавать весь файл настроек не требуется.
5. Альтернатива — файл `FFA_config`, который QLoader уже получил от сервера. Его можно импортировать на шлеме. При смене конфигурации поставщиком файл придётся импортировать повторно; при подключении ссылкой QCatalog получает свежий конфиг при обновлении каталога.

Не вставляй личную ссылку, `overrides.conf` или `FFA_config` в публичные issues. В ссылке могут находиться данные доступа. Для сообщения об ошибке достаточно версии приложения, модели шлема, шага воспроизведения и текста ошибки без адресов и токенов.

### 2. Установить QCatalog на Quest

Понадобятся Quest с включённым режимом разработчика, USB-кабель с передачей данных и Android SDK Platform Tools (`adb`) на компьютере. Подготовка по [официальной инструкции Meta](https://developers.meta.com/vr/documentation/native/android/mobile-device-setup/):

- Создай команду разработчиков или вступи в неё в Meta Developer Dashboard и пройди проверку своего аккаунта. В инструкции Meta указаны возраст от 18 лет и отсутствие ограничений устройства.
- В Meta Horizon на телефоне выбери значок шлема → свой Quest → Headset Settings → Developer Mode и включи режим разработчика.
- Для Windows установи [Oculus ADB Drivers](https://developers.meta.com/vr/downloads/package/oculus-adb-drivers/), если нужный драйвер ещё не установлен. Подробности и актуальные требования смотри в инструкции Meta.

1. Скачай `QCatalog-0.5.0-preview.apk` из [Releases](https://github.com/RoyMatus/qcatalog-quest/releases/tag/v0.5.0-preview).
2. Подключи Quest по USB и разреши USB-отладку **внутри шлема**. Отображение «Внутреннего общего накопителя» в Проводнике означает доступ к файлам, но ещё не подтверждает разрешение отладки.
3. В PowerShell, открытом в папке Platform Tools, проверь устройства:

   ```powershell
   .\adb.exe devices -l
   ```

4. Найди именно свой Quest со статусом `device`. Если указано `unauthorized`, подтверди запрос в шлеме. В следующих командах замени `QUEST_SERIAL` на серийный номер своего Quest, а путь к APK — на путь скачанного файла:

   ```powershell
   .\adb.exe -s QUEST_SERIAL install -r 'C:\Downloads\QCatalog-0.5.0-preview.apk'
   ```

5. Открой библиотеку приложений Quest → «Неизвестные источники» → **QCatalog**. Расположение фильтра зависит от версии интерфейса Quest.

При обновлении QCatalog устанавливай новый APK поверх старого, когда загрузки и установки закончены. Не удаляй приложение перед обычным обновлением: удаление стирает его подключение, историю и собственные скачанные файлы.

### 3. Перенести подключение в шлем

Выбери один из трёх способов.

**А. Автоматически с Windows-компьютера**

Установи APK из этого репозитория. Скачай исходники, открой в их папке **PowerShell 7**. Укажи свой серийный номер, папку данных QLoader и путь к `adb.exe`:

```powershell
.\connect-quest.ps1 -Serial 'QUEST_SERIAL' `
  -QLoaderDirectory 'C:\Path\To\QLoaderData' `
  -AdbPath 'C:\Path\To\platform-tools\adb.exe'
```

Сценарий читает только нужное значение `ConfigUpdateUrl` из локального `overrides.conf`. Он не печатает ссылку, передаёт её через стандартный ввод в закрытую папку QCatalog и перезапускает приложение. QCatalog шифрует ссылку через Android Keystore и удаляет временный файл импорта. Сценарий предназначен для текущей отладочной preview-сборки; он не работает с произвольным APK, запрещающим `run-as`. Запускай его только после завершения загрузок и установок.

**Б. Вставить ссылку в QCatalog**

1. Открой «Подключение».
2. Вставь полную HTTPS-ссылку из `ConfigUpdateUrl`, включая часть после `?`, если она есть. Вставляй значение без имени параметра и без кавычек.
3. Нажми «Подключить» и дождись каталога. Поле ввода скрывает символы ссылки.

**В. Импортировать файл `FFA_config`**

1. Скопируй свой `FFA_config` с компьютера в папку `Download` внутреннего накопителя Quest через USB.
2. В QCatalog открой «Подключение» → «Импорт файла» и выбери этот файл в системном окне.
3. Дождись загрузки каталога. После успешного импорта исходную копию из `Download` можно удалить вручную: QCatalog использует свою внутреннюю копию.

При импорте выбирается именно файл конфигурации rclone, а не `overrides.conf`, не журнал и не страница сайта.

### 4. Скачать, установить и обновить игру

1. Найди приложение и открой карточку → «Скачать».
2. Дождись статуса «Готово к установке» во вкладке «Загрузки».
3. Выбери запись → «Установить». При первом запуске разреши QCatalog устанавливать приложения через предложенный системный экран, затем вернись и повтори установку.
4. Подтверди установку в системном диалоге Android.
5. После статуса «Установлено» нажми «Открыть» либо запусти приложение из библиотеки Quest.

Если идентификатор пакета APK отличается от каталога, QCatalog покажет оба значения и запросит отдельное подтверждение. Оно действует только для конкретного файла с проверенным SHA-256. Несовпадение числового кода версии блокирует установку.

Для обновлений выбери «Обновить каталог» (в узком окне — «Разделы → Настройки → Обновить каталог»), затем открой раздел «Обновления». Карточка показывает установленную версию и версию каталога. Кнопка «Обновить» скачивает новый релиз, а сама установка по-прежнему требует системного подтверждения.

Перед установкой QCatalog проверяет доступные сведения о подписи. При явно разных ключах установленного приложения и APK он объясняет конфликт заранее. История ротации сертификатов учитывается; окончательную совместимость решает Android. Ошибки установщика показываются как «Ошибка установки» с причиной, а не как «Готово к установке, код 5». Повторная попытка использует уже скачанный APK.

Приложение не удаляет установленную игру автоматически. Для замены на сборку с другой подписью потребуется отдельно удалить старую версию; это может стереть сохранения и моды. Простое повторение установки или скачивания конфликт подписи не устраняет.

Проверка версий работает и для игр, установленных другими способами, если совпадает идентификатор пакета. Название игры и дата релиза не используются для определения новизны. Если Android-пакет отличается, автоматическое сопоставление возможно только для ранее подтверждённого расхождения. Удаление истории это сопоставление не сбрасывает.

### 5. Освободить место и очистить историю

Во вкладке «Загрузки» → «Очистка файлов и истории»:

| Действие | Результат |
| --- | --- |
| Удалить скачанные файлы | Удаляются APK и прочие файлы релизов из собственной папки загрузок QCatalog. Установленные приложения и их сохранения остаются. |
| Очистить историю загрузок | Удаляются записи списка. Скачанные файлы остаются на месте. |

Для одной загрузки: запись → «Ещё» → нужное действие. Перед удалением появляется подтверждение. Общая очистка находит и файлы релизов, записи которых уже удалены из истории. Во время активных загрузок и установок очистка блокируется. После новой успешной установки/обновления установочные файлы автоматически удаляются, а задача исчезает из активных загрузок. Ошибка установки сохраняет файлы; ошибка очистки допускает отдельный повтор. Старые завершённые записи автоматически не очищаются.

### Описания, русский язык и изображения

Описания загружаются **только при открытии карточки**, обложки — только для видимых карточек, без предварительного обхода каталога. Оригинальное описание приходит из API источника QLoader. Если оно уже русское, показывается короткий фрагмент. Если источник отдаёт английский текст, короткий фрагмент переводится через [MyMemory](https://mymemory.translated.net/doc/spec.php) в момент открытия. Это машинный перевод, его точность не гарантируется. Полный исходный текст доступен по кнопке «Оригинал описания».

У анонимного доступа MyMemory есть [лимит 5 000 символов в день на IP](https://mymemory.translated.net/doc/usagelimits.php). Число доступных переводов зависит от длины текста, повторных открытий и использования того же внешнего IP другими людьми. При исчерпании лимита или недоступности сервиса приложение сообщает об этом и оставляет оригинал. В MyMemory отправляется только публичный фрагмент описания, без ссылки доступа или конфига QLoader.

Обложка читается из метаданных каталога в оперативную память. Описания, переводы и картинки не имеют файлового кэша: при закрытии карточки запросы отменяются, ссылки на содержимое освобождаются; при повторном открытии данные запрашиваются заново. Сам список каталога, история и скачанные установочные файлы сохраняются — это отдельные данные.

В версии 0.5.0 есть крупная карусель обложки, скриншотов и видео. Доступность зависит от источника: у некоторых игр медиа отсутствует или удалено. Скриншоты берутся из публичной страницы Meta по ID QLoader, трейлер также доступен с зеркала QLoader. Подробнее — в разделе о карусели ниже.

### Ограничения и проверка

- Подтверждены получение каталога, скачивание на Quest 3S и установка одного обычного APK через Android. Запуск самой установленной игры в рамках проверки не проверялся.
- Для `install.txt` поддерживаются установка одного APK и копирование данных `adb push`, в том числе из архивов 7z. Пути и содержимое архива проверяются; команды оболочки не запускаются. Инструкции удаления старого APK/данных не выполняются: существующие сохранения остаются, файлы поставки заменяются проверенными копиями. Неизвестные команды показывают конкретную ошибку, скачанные файлы сохраняются. Несколько APK и split APK пока не поддерживаются.
- Копирование OBB реализовано, но успешная установка OBB на Quest 3S **не подтверждена**. По [документации Android](https://developer.android.com/training/data-storage/manage-all-files), доступ ко всем файлам не отменяет ограничения чужих каталогов `Android/obb`. При отказе записи QCatalog отдельно сообщает, что APK установлен, а данные не завершены.
- Для загрузок, начатых в 0.3.0, позиция внутри файла сохраняется на диск. После повторного открытия QCatalog или загрузки шлема очередь продолжается автоматически; ручная «Пауза» сохраняется и сама не отменяется. Полностью скачанные файлы повторно не загружаются. При обычной паузе сохраняется принятый байт; при аварийном выключении используется последний надёжный checkpoint, записываемый примерно каждые 4 МиБ. Если параметры исходного неполного файла изменились, он загружается заново. Частичные файлы старых версий не имеют этих checkpoints и могут потребовать нового скачивания.
- Автозапуск после включения зависит от системного события загрузки и доступности сети/разблокировки. После «Принудительно остановить» в настройках Android нужно снова открыть QCatalog. Сам шлем в ходе проверки не перезагружался, чтобы не прерывать пользовательскую очередь; обработчик события проверяется в отдельном тесте.
- Это ARM64 preview-сборка с `debuggable=true`, не магазинный production-релиз. Отладочный доступ нужен, в частности, для помощника переноса настроек. Не предоставляй доступ ADB постороннему компьютеру.
- Форматы и доступность серверов поставщика могут измениться. Все релизы каталога и другие модели Quest не проверены.
- Форматирование описаний проверено на синтетических данных. Массовая загрузка описаний, переводов и изображений для подготовки релиза не выполнялась; показ живой карточки на шлеме отдельно не подтверждён.

### Приватность

Персональная ссылка хранится с шифрованием Android Keystore в закрытой папке приложения. Конфиг хранится там же в формате поставщика: зашифрованный файл остаётся зашифрованным, но для импортированного открытого конфига дополнительное шифрование не добавляется. Резервное копирование Android отключено. Полные сетевые адреса скрываются в сообщениях об ошибках приложения. Шлем обращается к серверам поставщика и, при необходимости перевода, MyMemory; собственного сервера QCatalog нет.

### Сборка из исходников

Нужны **Windows, PowerShell 7, JDK 21, Android SDK Build Tools 36.1.0 и SDK Platform android-36.1**. Gradle не используется. Загрузчик rclone берётся из публичного ARM64-дистрибутива поставщика; его происхождение и лицензия исходного rclone указаны в [THIRD_PARTY_NOTICES.txt](THIRD_PARTY_NOTICES.txt).

```powershell
# Скачивает архив (~322 MiB), проверяет SHA-256 и извлекает только rclone.
# Установочный сценарий поставщика не запускается.
.\fetch-engine.ps1

.\build.ps1 -EnginePath '.\native\linux-arm64\rclone' `
  -JavaHome 'C:\Path\To\jdk-21' -SdkPath 'C:\Path\To\Android\Sdk'
```

Если проверенный архив уже есть, используй `fetch-engine.ps1 -ArchivePath 'C:\Path\To\linux-arm64.tar'`. Если поставщик изменит архив, проверка хэша остановит извлечение. Не отключай её без проверки нового файла.

Результат: `build/QCatalog-0.5.0-preview.apk`. Сборка проверяет подпись APK и запускает тесты каталога, безопасного удаления файлов и форматирования описаний. Исходники модифицированного бинарного движка поставщика в репозиторий не входят; этот движок не пересобирается из исходников.

Для обновления поверх установленного APK нужен тот же ключ подписи. `build.ps1` создаёт локальный `build/debug.keystore`, если его ещё нет. Сохрани свой ключ отдельно; он исключён из Git. Самостоятельная сборка с новым ключом не установится как обновление готового APK из Releases.

Проверки на Quest после сборки и установки APK, подключения конфига и получения каталога:

```powershell
.\test-quest.ps1 -Serial 'QUEST_SERIAL' `
  -JavaHome 'C:\Path\To\jdk-21' -SdkPath 'C:\Path\To\Android\Sdk'
```

Сценарий временно устанавливает тестовый пакет с тем же ключом и удаляет его после проверки. Он читает настоящий список установленных приложений, но проверяет удаление на отдельной временной папке; пользовательские загрузки не удаляет. Не запускай во время работы с QCatalog: instrumentation перезапускает процесс приложения. Описания и изображения тестом не запрашиваются.

`test-storage.ps1` принимает те же параметры и проверяет шифрование ссылки и пустую ссылку при импорте файла в отдельном тестовом приложении. Он не останавливает основной QCatalog и не обращается к его данным. Дополнительные параметры `-PreviousApk` и `-IncomingApk` позволяют проверить подписи двух локальных APK относительно установленного приложения: ожидается, что первый конфликтует, а второй имеет совместимую подпись. Тест не устанавливает эти APK и удаляет временные копии вместе со своим тестовым пакетом.

### Участие и доступ к репозиторию

Код и готовые релизы доступны для чтения и скачивания, issues открыты для сообщений об ошибках. Запись в этот репозиторий и выпуск релизов доступны владельцу. GitHub также позволяет форки и предложения через pull request; они сами по себе не дают права менять исходный репозиторий. Не публикуй персональные конфиги в обсуждениях.

---

## English

From 0.4.2 game cards are more compact: 100 dp covers instead of 152 dp, reduced spacing, five columns at 1920 dp and four at 1280 dp. Library cards show a green check labelled «Есть обновление» only when the catalogue version is newer than the installed version.


### What it does

QCatalog is a standalone Android app with a windowed interface for Meta Quest. Once installed and connected, the headset retrieves the catalogue, downloads releases and opens Android's package installer itself. A PC is not required for these everyday operations.

Anyone can install the same APK and supply **their own** compatible QLoader configuration. There is no username/password sign-in screen: connect using an HTTPS configuration URL or import an `FFA_config` file. The APK and repository contain no personal access URL, user configuration, account or game files.

This is an independent experimental client, not an official Meta, QLoader or dipVR product. Compatibility has been checked with the dipVR / VR Games RUS distribution of QLoader 1.2.0. Other providers have not been verified. The current app interface is in Russian; the instructions below include the corresponding button labels.

Features include catalogue search, download queue and pause, durable byte-offset resume with automatic app/headset startup recovery, Android-confirmed APK installation, installed/update filters, comparison of actual installed version codes, separate download-file and history cleanup, and Russian summaries with covers loaded when a card is opened.

### 1. Obtain your configuration using the Telegram instructions

Verified provider links:

- [dipVR channel](https://t.me/dipVR).
- [QLoader post and setup links](https://t.me/dipVR/178).
- [VR Games RUS support group](https://t.me/VRGamesRUS).
- [Windows download without trailers](https://cloud.dipvr.ru/download/no-trailers/), reached from that post.

The public Telegram post links to the QLoader distribution. A separate Telegram bot or command that directly issues a configuration has **not** been verified. The verified route is to obtain the configuration through a working desktop QLoader installation:

1. Open the Telegram post and use the provider's download link for your operating system. The Windows package without trailers is sufficient.
2. Install and run QLoader according to the provider's instructions. Make sure its catalogue opens. QCatalog does not create access to the provider's servers.
3. Find QLoader's data folder. The Windows instructions specify `%LOCALAPPDATA%\QLoader`; a portable installation may keep data beside `Loader.exe`. The startup log identifies the actual folder as `Data directory`.
4. Open `overrides.conf` in that folder. The full value after `ConfigUpdateUrl=` is the configuration URL to use in QCatalog. Do not copy the parameter name or surrounding quotes.
5. Alternatively, use the `FFA_config` file already fetched by QLoader. A file import needs to be repeated if the provider changes the configuration. A URL connection fetches the configuration again whenever you refresh the catalogue.

If the provider changes its access process, ask in its support group for the current instructions. Never post your URL, `overrides.conf` or `FFA_config` in a public issue: they may contain access credentials.

### 2. Install the APK on Quest

You need a Quest with Developer Mode enabled, a USB data cable, and Android SDK Platform Tools (`adb`) on your PC. Follow [Meta's official device setup guide](https://developers.meta.com/vr/documentation/native/android/mobile-device-setup/):

- Join or create a developer team in Meta Developer Dashboard and verify your account. Meta's guide requires age 18+ and no device restrictions that prevent Developer Mode.
- In the phone's Meta Horizon app, select the headset icon → your Quest → Headset Settings → Developer Mode, and enable it.
- On Windows, install [Oculus ADB Drivers](https://developers.meta.com/vr/downloads/package/oculus-adb-drivers/) if not already installed. Refer to Meta's guide for the current requirements and details.

1. Download `QCatalog-0.5.0-preview.apk` from [Releases](https://github.com/RoyMatus/qcatalog-quest/releases/tag/v0.5.0-preview).
2. Connect the Quest over USB and allow USB debugging **inside the headset**. Seeing the headset's shared storage in Windows Explorer does not mean USB debugging has been authorized.
3. Open PowerShell in the Platform Tools directory and run:

   ```powershell
   .\adb.exe devices -l
   ```

4. Identify your Quest with the `device` status. For `unauthorized`, accept the debugging prompt inside the headset. Replace `QUEST_SERIAL` below with your Quest's serial, and adjust the APK path:

   ```powershell
   .\adb.exe -s QUEST_SERIAL install -r 'C:\Downloads\QCatalog-0.5.0-preview.apk'
   ```

5. Open the Quest app library → **Unknown Sources** → **QCatalog**. The filter's location depends on the Quest software version.

For updates, install the new QCatalog APK over the existing app after downloads/installations finish. Uninstalling QCatalog removes its connection settings, history and app-owned downloads.

### 3. Transfer your connection to the headset

Choose one of these methods.

**A. Windows transfer helper**

Install this repository's APK, download the source files, and open **PowerShell 7** in the source directory. Supply your Quest serial, actual QLoader data directory and `adb.exe` path:

```powershell
.\connect-quest.ps1 -Serial 'QUEST_SERIAL' `
  -QLoaderDirectory 'C:\Path\To\QLoaderData' `
  -AdbPath 'C:\Path\To\platform-tools\adb.exe'
```

The helper reads `ConfigUpdateUrl` locally, does not print it, transfers it through standard input to the app's private directory, and restarts QCatalog. The app encrypts the URL with Android Keystore and removes the temporary import file. This helper requires the current debuggable preview APK and `run-as`; it cannot provision an arbitrary non-debuggable build. Run it only after downloads and installations finish.

**B. Paste the URL in QCatalog**

Open **«Подключение» (Connection)**, paste the complete HTTPS configuration URL, including any query after `?`, then press **«Подключить» (Connect)**. Paste the value only, without `ConfigUpdateUrl=` or quotes. The input masks the URL.

**C. Import `FFA_config`**

Copy your `FFA_config` to the headset's shared `Download` folder via USB. Open **«Подключение» → «Импорт файла» (Import file)** and select that file in Android's picker. Wait for the catalogue to appear. Once import succeeds, you can manually remove the copy in `Download`: QCatalog retains its own internal copy. Do not select `overrides.conf`, a log, or a web page instead of the rclone configuration.

### 4. Download, install and update apps

1. Find an app, open its card and press **«Скачать» (Download)**.
2. Under **«Загрузки» (Downloads)**, wait for **«Готово к установке» (Ready to install)**.
3. Select the item and press **«Установить» (Install)**. On first use, allow QCatalog to install apps through the offered Android settings screen, return and try Install again.
4. Confirm the Android installation dialog.
5. After **«Установлено» (Installed)**, use **«Открыть» (Open)** or launch the app from the Quest library.

If the APK's package identifier differs from the catalogue, QCatalog shows both and requires explicit confirmation tied to the SHA-256 of that exact APK. A different version code blocks installation.

For updates, press the top **«Обновить» (Refresh)** button to retrieve the latest catalogue, then select **«Обновления» (Updates)**. The card shows installed and catalogue versions. Its **«Обновить» (Update)** button downloads the new release; installing it still requires Android confirmation.

Before installation QCatalog checks available signing information. Clearly unrelated signing keys produce an explanation before opening the installer. Certificate rotation histories are considered; Android remains the final authority. Installer failures appear as **«Ошибка установки» (Installation error)** with the reason, instead of a misleading ready-to-install status and code 5. A retry reuses the downloaded APK.

QCatalog does not automatically uninstall existing apps. Replacing an app with a differently signed build requires separately removing the old version, which may delete saves and mods. Simply retrying or downloading again does not resolve a signature mismatch.

Installed versions come from Android, including games installed by other tools, when package identifiers match. Updates compare numeric `versionCode`, not release dates or marketing version strings. Previously confirmed package aliases survive history cleanup.

### 5. Delete downloads and clear history

Under **«Загрузки» → «Очистка файлов и истории» (File and history cleanup)**:

| Action | Effect |
| --- | --- |
| «Удалить скачанные файлы» / Delete downloaded files | Removes downloaded APKs and other release files from QCatalog's own downloads folder. Installed apps and their saves remain. |
| «Очистить историю загрузок» / Clear download history | Removes list entries while keeping downloaded files. |

For one item, open it → **«Ещё» (More)** → select the action. Cleanup asks for confirmation and is blocked during active downloads/installations. Bulk file cleanup also finds downloads whose history entries have already been removed. New successful installs/updates automatically delete their staging files and leave the active download list. Failed installs retain source files; failed cleanup has a separate retry. Legacy completed tasks are not automatically migrated into deletion.

### Descriptions, Russian translation and images

Only opening a card triggers its metadata requests; there is no catalogue-wide prefetch. Original descriptions come from the current QLoader provider's API. Russian text is shortened directly. English text is reduced to a short fragment and translated on demand with [MyMemory](https://mymemory.translated.net/doc/spec.php). Machine translation can be inaccurate. **«Оригинал описания» (Original description)** shows the full source text.

Anonymous MyMemory access has a [5,000-character daily limit per IP](https://mymemory.translated.net/doc/usagelimits.php). The number of translations depends on text length, repeated openings and other people sharing that external IP. If translation is unavailable or exhausted, the app explains this and retains access to the original. Only the public description fragment is sent to MyMemory, never the QLoader configuration or access URL.

Visible covers are streamed from catalogue metadata into a bounded 16 MiB RAM cache. Descriptions, translations and images are not written to a disk cache; closing the card cancels requests and releases content references. Reopening a card requests the content again. The catalogue list, queue/history and downloaded installation files are persisted separately.

Version 0.5.0 includes a large cover/screenshot/video carousel. Availability depends on the source: some games have no accessible media. Screenshots use the public Meta page identified by QLoader; trailers can also come from the QLoader mirror. See the carousel section below.

### Limitations, validation and privacy

- Catalogue retrieval, direct downloading and installation of one ordinary APK were verified on Quest 3S. The installed game's own runtime behavior was not tested.
- `install.txt` supports one APK and declarative `adb push` data placement, including 7z archives. Archive entries and paths are validated; no shell is launched. Legacy uninstall/delete instructions are not executed: existing saves remain and supplied files are replaced with verified copies. Unknown commands produce an explicit error while keeping downloads. Multiple APKs and split APK remain unsupported.
- OBB copying is implemented but successful OBB installation on Quest 3S is **unverified**. [Android's all-files access](https://developer.android.com/training/data-storage/manage-all-files) does not remove restrictions on other apps' `Android/obb` directories. A data-copy failure is reported separately from APK installation.
- Downloads started with 0.3.0 persist a byte checkpoint. Reopening QCatalog or receiving the headset boot event restores pending work automatically; a manual pause remains paused. Completed files are reused. Normal pause saves the received byte position; abrupt power loss resumes from the last durable checkpoint, written roughly every 4 MiB. A changed partial-file identity restarts that file. Older versions' partial files lack these checkpoints and may need downloading again.
- Boot restart depends on Android delivering the boot event and network/unlock availability. After Android's Force stop action, reopen QCatalog first. The headset was not rebooted during validation to preserve the user's active queue; boot handling is tested in isolation.
- This is an ARM64 preview with `debuggable=true`, not a production store build. Debug access is used by the provisioning helper. Do not authorize ADB access for an untrusted computer.
- Provider formats and server availability can change. Compatibility with every catalogue release or other Quest models has not been established.
- Description formatting was tested with synthetic content. No catalogue-wide translations or image downloads were performed for release preparation; live card rendering on the headset has not been separately verified.

The access URL is encrypted with Android Keystore in the app's private directory. The configuration is kept there in the provider's format: encrypted input remains encrypted, but an imported plaintext configuration does not gain extra encryption. Android backup is disabled. App error messages hide full network URLs. The headset contacts the provider and, when translation is needed, MyMemory; QCatalog has no separate backend.

### Build and test

Requirements: **Windows, PowerShell 7, JDK 21, Android SDK Build Tools 36.1.0, SDK Platform android-36.1**. No Gradle dependency. The ARM64 download engine comes from the provider's public archive; see [THIRD_PARTY_NOTICES.txt](THIRD_PARTY_NOTICES.txt) for provenance and the upstream rclone license.

```powershell
# Downloads about 322 MiB, verifies SHA-256 and extracts only rclone.
# Does not execute the provider's installer.
.\fetch-engine.ps1

.\build.ps1 -EnginePath '.\native\linux-arm64\rclone' `
  -JavaHome 'C:\Path\To\jdk-21' -SdkPath 'C:\Path\To\Android\Sdk'
```

Use `fetch-engine.ps1 -ArchivePath 'C:\Path\To\linux-arm64.tar'` for an existing archive. A changed provider archive fails the pinned hash check; review it before changing the expected hash.

Output: `build/QCatalog-0.5.0-preview.apk`. The build verifies the APK signature and runs catalogue, download-containment/deletion and description-formatting tests. The provider's modified native engine source is not included or rebuilt here.

The build creates a local `build/debug.keystore` when absent. Keep your signing key privately: it is excluded from Git, and updates require the same key. Your own rebuild with a new key cannot update the APK distributed in Releases.

After building and installing the APK, connecting a config and fetching the catalogue:

```powershell
.\test-quest.ps1 -Serial 'QUEST_SERIAL' `
  -JavaHome 'C:\Path\To\jdk-21' -SdkPath 'C:\Path\To\Android\Sdk'
```

This temporarily installs a test package with the same signing key and removes it afterwards. It reads actual installed versions, but tests file deletion using isolated fixtures and preserves user downloads/history. Run it only while idle: instrumentation restarts the app process. Tests do not request descriptions, translations or images.

`test-storage.ps1` takes the same parameters and checks URL encryption and the empty URL used by file imports in a separate test app. It neither stops the real QCatalog app nor accesses its data. Optional `-PreviousApk` and `-IncomingApk` parameters check two local APKs against the installed app: the first is expected to conflict, and the second to have a compatible certificate. These APKs are not installed; the test package and its temporary copies are removed afterwards.

### Repository access and issues

Source and releases are public to read and download; issues are open for bug reports. Repository writes and releases are controlled by the owner. GitHub also permits forks and pull-request proposals, which do not grant write access to this repository. Include the app version, headset model, reproduction steps and sanitized error text in reports; never attach personal configuration files.


### Проверки 0.4.1 / 0.4.1 validation

22 изолированные UI-проверки: совпадение верхних/нижних границ «Пауза»/«Установить» с соседней кнопкой, обе системные темы, шрифт 160%, перенос в узкой области и фактический запуск панели 1920 × 1080 dp на Quest 3S. Глобальная тема шлема в тестах не менялась.

22 isolated UI checks cover button bounds, both resource night modes, 160% font scale, narrow fallback and actual 1920 × 1080 dp launch on Quest 3S. Tests did not change the headset's global theme. Horizon OS still controls the panel's placement and physical/angular size in VR; the application fills its assigned panel. Sources: [Meta panel sizing](https://developers.meta.com/vr/essentials/horizon-os-panel-sizing/), [Android activity layout](https://developer.android.com/guide/topics/manifest/layout-element).

### Проверки 0.4.2 / 0.4.2 validation

29 изолированных UI-проверок на Quest 3S: компактная сетка 5/4 колонки, зелёная отметка только новой версии, отсутствие ложной отметки при повторном использовании карточки, цели VR от 64 dp; сохранены проверки действий и тем.

29 isolated UI checks on Quest 3S cover compact 5/4-column grids, green checks only for newer releases, recycled-card reset and 64 dp minimum VR targets, alongside action alignment and theme checks.

### Дополнительная установка / Special installation (0.4.3)

Релизы портов вроде Half-Life / XASH могут содержать `Install.txt`, APK и большой архив `.7z`. QCatalog распаковывает архив во временную папку внутри скачанного релиза, проверяет CRC, затем переводит `adb install` и `adb push` в системную установку APK и проверенное копирование файлов. Регистр имён источников учитывается без неоднозначных совпадений (`xash` / `XASH`). Полная распаковка повторно используется; после прерывания незавершённая распаковка перезапускается из уже скачанного архива. Сам архив повторно скачивать не требуется. Для распаковки и копирования нужно дополнительное свободное место.

Команды `uninstall` и `rm -rf` не выполняются: QCatalog сохраняет существующие файлы вне поставки, включая сохранения, и заменяет только поставляемые файлы. Поэтому это поддержка известных инструкций размещения, а не произвольного командного сценария. Для данных в `/sdcard/XASH` нужен «Доступ ко всем файлам». После успешной установки и проверки удаляется только скачанный релиз QCatalog, не установленная папка XASH.

Старая ошибка о неподдерживаемом install.txt при запуске меняется на «Файлы сохранены. Нажми “Установить”». Перед установкой файлы проверяются повторно. Удалять игру или запускать загрузку заново не нужно.

Port releases such as Half-Life / XASH may contain `Install.txt`, an APK and a large `.7z` archive. QCatalog extracts locally inside the downloaded release with CRC/path checks, then translates `adb install` and `adb push` into Android package installation and verified data copying. Source lookup handles case without ambiguous matches. Completed extraction is reused; interrupted extraction retries from the saved archive without downloading it again. Additional free space is required for extraction and copying.

Legacy `uninstall` / `rm -rf` commands are not executed. Existing files outside the supplied set, including saves, remain; supplied files are replaced. This supports known placement instructions, not arbitrary scripts. Shared game folders such as `/sdcard/XASH` require All files access. Successful cleanup removes only QCatalog's downloaded release, not installed XASH files.

The old unsupported-install.txt error becomes a ready-to-install task on launch. Files are rechecked before installation. Do not delete or redownload the game merely to clear this old error.

Build dependencies are pinned by SHA-256 in `dependencies.json`. `build.ps1` and `test-storage.ps1` run `fetch-dependencies.ps1`; the first build requires Maven Central access. Libraries are embedded in the APK, so headset extraction needs neither a PC nor a library download. Original licenses are bundled under `assets/licenses/`.

Проверка 0.4.3 на Quest 3S: 53 изолированных проверки хранения/очереди/распаковки и 29 host-проверок дополнительных инструкций/архивов. Реальный скачанный релиз Half-Life / XASH успешно распакован: 13 112 файлов, 4 368 536 271 байт; версия APK совпала с каталогом. Проверка не устанавливала игру и не меняла её существующие данные.

0.4.3 validation on Quest 3S: 53 isolated storage/queue/archive checks and 29 host special-install/archive checks. An existing Half-Life / XASH download was successfully expanded into 13,112 files (4,368,536,271 bytes); its APK version matched the catalogue. This validation neither installed the game nor changed its existing data.

Optional `test-quest.ps1 -SpecialReleaseId RELEASE_ID` expands an existing downloaded release inside QCatalog's staging folder and validates its installation plan, preserving the queue and installed games. Run only while idle. Unlike the default isolated fixtures, this opt-in check retains the expanded release for subsequent installation.

### Версии и карусель / Versions and media carousel (0.5.0)

На каждой карточке каталога, библиотеки и обновлений показаны версия из имени конкретного релиза и числовой код: например `v1.40.8_7379 · код 1716`. Если в имени релиза нет версии, отображается только известный код. Это не «последняя версия магазина»: разные выпуски одного пакета не подменяются одним номером.

Окно игры занимает 90% панели. Вместо маленькой обложки — карусель с крупным кадром 360 dp: обложка, скриншоты, затем видео. Переключение стрелками или горизонтальным жестом по изображению; счётчик показывает текущий кадр. «Увеличить» открывает изображение почти на всю панель. Видео запускается только кнопкой, поддерживает паузу и перемотку. При переходе на другой кадр и сворачивании приложения воспроизведение приостанавливается; закрытие карточки освобождает плеер и память.

Источники проверяются динамически: API QLoader, публичная страница Meta по идентификатору из QLoader (для свежих ссылок на скриншоты/видео), изображения в папке релиза, `.meta/videos/PACKAGE.mp4` зеркала QLoader. Галерея Meta может отсутствовать у удалённых из магазина игр; медиа недоступных источников не выдумывается. Связь изображений и видео с идентификатором приложения проверяется, похожие и рекомендуемые игры не смешиваются с выбранной.

Метаданные, выбранное изображение и видеобуфер находятся только в оперативной памяти. Скриншоты не загружаются все сразу; видео читается только после нажатия кнопки. Дисковых файлов и кэша медиа приложение не создаёт. Лимит видеобуфера — 64 МиБ; слишком большой или неподдерживаемый ролик выдаёт сообщение, но не мешает установке игры. Личные данные QLoader не отправляются на Meta или переводчику.

Each catalogue/library/update card shows its own release version and numeric code, for example `v1.40.8_7379 · код 1716`. Missing marketing versions are not invented, and store-latest metadata never replaces per-release version labels.

Game details occupy 90% of the panel. A single-frame carousel shows a large 360 dp cover, screenshots and a video item. Use arrows or a horizontal image swipe; the counter tracks the selected item. Image enlargement uses almost the entire panel. Video starts only on demand and supports pause/seeking. Selecting a different frame or backgrounding the app pauses playback; closing the details frees player and buffers.

Sources are queried dynamically: QLoader API, the public Meta page identified by QLoader (fresh screenshot/video links), images in the release folder, and the QLoader mirror's `.meta/videos/PACKAGE.mp4`. Missing or delisted media stays unavailable rather than being fabricated. Structured media references are scoped to the chosen application, excluding recommendations for other games.

Metadata, the selected image and a bounded video buffer stay in RAM only. Screenshots are not prefetched as a set; videos load only after a button press. The app creates no media files or disk cache. Video buffering is limited to 64 MiB; unsupported/oversized videos show an error without blocking installation. QLoader credentials are never sent to Meta or translation services.

MyMemory остаётся переводчиком без ключа API. Это не оценка качества Google: официальный Google Cloud Translation требует отдельный проект, авторизацию и включённый биллинг; бесплатный объём для стандартного NMT-перевода — 500 000 символов в месяц, затем оплата. Бесплатный сайт Google Translate не является безлимитным API для приложения. [Настройка](https://docs.cloud.google.com/translate/docs/setup), [тарифы](https://cloud.google.com/products/translate/pricing).

MyMemory remains the keyless translation option. Google Cloud Translation requires a project, authenticated access and billing; standard NMT translation has a 500,000-character monthly free allowance followed by usage charges. The consumer Google Translate website is not an unlimited app API. See the official setup/pricing links above.

`test-storage.ps1 -LiveMedia` checks one actual game's screenshot and native video prepare/play/pause/seek inside an isolated test app, and verifies that app files/cache gain no files. `test-quest.ps1 -LiveMedia` repeats this using the installed app and its configured mirror, while preserving queue/configuration. Both options are opt-in network checks; use the installed-app check only while idle.

Проверки 0.5.0: 38 изолированных UI-проверок, 72 проверки хранения/очереди/медиа в отдельном приложении с реальным скриншотом и видео, 36 проверок установленного приложения с подтверждённым чтением трейлера с настроенного зеркала QLoader. Воспроизведение, пауза, перемотка и отсутствие новых файлов в files/cache проверены; очередь не изменилась.

0.5.0 validation: 38 isolated UI checks, 72 isolated storage/queue/media checks with a real screenshot/video, and 36 installed-app checks that assert use of the configured QLoader mirror trailer. Playback, pause, seeking, no added files/cache entries and unchanged queue were verified.
