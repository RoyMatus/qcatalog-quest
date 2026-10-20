# QCatalog for Meta Quest

Каталог QLoader прямо на шлеме: поиск, скачивание, установка APK и проверка обновлений.

**[Скачать APK / Download APK](https://github.com/RoyMatus/qcatalog-quest/releases/tag/v0.6.3-preview)** · [Issues](https://github.com/RoyMatus/qcatalog-quest/issues) · [Русский](#русский) · [English](#english)

Версия / Version: **0.6.3-preview**. Целевая платформа / Target: **Quest 3S, ARM64, Android API 34**. Проверки этой версии не запускались по просьбе пользователя / This version was not tested at the user's request.

[История изменений / Changelog](CHANGELOG.md)

В 0.6.3 под надписью «Загрузка» отображается счётчик «Загружено 3 из 10»: учитываются только фотографии, успешно декодированные и сохранённые в RAM, включая обложку. Пока список фотографий неизвестен, общее количество отмечено многоточием. Ошибки не засчитываются как загруженные фотографии.

In 0.6.3, the loading overlay counts successfully decoded RAM-cached photos, including the cover. The total remains unknown until photo discovery finishes; failed requests do not count as downloaded images.

Видео: текущий клиент получает отдельный MP4 и воспроизводит его в исходном качестве. В проверенной карточке источник возвращает один вариант без адаптивного плейлиста. Автоматического выбора разрешения по скорости соединения в этой версии нет; для него нужны разные варианты одного видео от источника. / Video: the client plays a standalone MP4 at its original quality. The inspected game source exposes one rendition, not an adaptive playlist. This version does not adapt resolution to bandwidth; that requires multiple renditions of the same video from the source.

В 0.6.2 при открытии карточки поверх изображения отображаются полупрозрачная надпись «Загрузка» и три плавно переливающиеся точки до завершения предзагрузки всех фотографий. Индикатор не ждёт загрузки видео и снимается после завершения попыток, включая ошибки сети. Анимация освобождается при закрытии карточки; кэш фотографий остаётся только в RAM.

In 0.6.2, opening a game card shows a translucent loading label and three pulsing dots over the image until all photo prefetch attempts finish. Video is not part of this wait; network failures do not leave an endless indicator. Closing the card stops the animation; photos remain memory-only.

В 0.6.1 компьютеры в сети показаны плитками: имя сверху, IP ниже. Имя запрашивается у локальной сети; если оно недоступно, отображается только IP. / In 0.6.1, network computers appear as tiles: name first, IP below. Names come from network resolution; if unavailable, only the IP is shown.

## Русский

### Что это

QCatalog — отдельное Android-приложение с обычным оконным интерфейсом для Quest. После первоначальной установки и подключения шлем сам получает каталог, скачивает релизы и вызывает системный установщик. Компьютер для этих действий больше не нужен.

Приложение не привязано к одному человеку: другой пользователь устанавливает тот же APK и подключает **свой** совместимый конфиг QLoader. Логин и пароль внутри QCatalog не вводятся. Используется HTTPS-ссылка на конфигурацию либо файл `FFA_config`. Конфиги, аккаунты, игры и персональные ссылки в APK и этом репозитории отсутствуют.

Это самостоятельный экспериментальный клиент, не официальный продукт Meta, QLoader или dipVR. Проверено подключение к конфигурации дистрибутива QLoader 1.2.0 от dipVR / VR Games RUS. Совместимость с другими поставщиками не подтверждена.

### Возможности

- Каталог, библиотека установленных игр, загрузки и обновления; поиск по названию, релизу и пакету.
- Версия конкретного релиза и её числовой код на карточке: например `v1.40.8_7379 · код 1716`. Если в имени релиза нет версии, показывается только код.
- Сортировка А–Я или по популярности скачиваний QLoader за сутки / 7 / 30 дней. Это статистика скачиваний, не звёзды магазина Meta; отсутствующие значения сортируются последними.
- Скачивание прямо на шлем, очередь, пауза и продолжение с сохранённой позиции после закрытия приложения или перезапуска шлема.
- Установка APK через системное подтверждение Android; проверка пакета, версии и доступных сведений о подписи.
- Сравнение установленных версий с каталогом. В библиотеке зелёное **«✓ Есть обновление»** отмечает более новую версию каталога.
- Прогресс скачивания, подготовки APK, копирования и проверки данных. Когда Android не сообщает точный процент установки, показывается неопределённый индикатор.
- После успешной установки/обновления и проверки данных задача убирается из активных загрузок, а её установочные файлы удаляются. При ошибке файлы сохраняются для повторной попытки.
- Поддержка известных инструкций `install.txt` и распаковка 7z на шлеме; отдельная очистка скачанных файлов и истории.
- Русское описание и крупная карусель обложки, доступных скриншотов и видео. Медиа загружается по запросу и не сохраняется в файлы.

### Интерфейс

Используется [набор Cards v1](design/cards-v1/README.md). Основной каталог открывается развёрнутым в горизонтальном формате 1920 × 1080 dp; в сетке 5 колонок при ширине 1920 dp и 4 при 1280 dp. В узкой области боковое меню заменяется кнопкой «Разделы». Фон, текст, кнопки и диалоги следуют системной светлой/тёмной теме. Минимальная цель нажатия — 64 dp; кнопки действий выровнены и при нехватке места переходят в колонку.

Окно сведений об игре узкое: максимум 720 dp, с уменьшением под доступную область. Карусель показывает один крупный кадр без подписей и счётчика. По бокам — полупрозрачные стрелки без залитых полос; они появляются при наведении указателя или фокусе и скрываются после ухода. Изображения можно листать горизонтальным жестом. Стик влево/вправо переключает кадр сразу в увеличенном просмотре, а также в карточке, когда указатель наведён на медиа. Каждое отдельное отклонение переключает кадр сразу; интервал в секунду действует только при непрерывном удержании. Horizon OS передаёт Touch-стики как горизонтальную прокрутку ACTION_SCROLL / AXIS_HSCROLL; события обычных геймпадов также поддерживаются. Возврат стика в центр, потеря фокуса, сворачивание приложения или выход из увеличенного просмотра прекращают повтор. Нажатие на изображение или сам кадр видео увеличивает его почти на всю панель, повторное нажатие возвращает обратно. Отдельных кнопок увеличения, закрытия и подложки нет. Размер видео меняется с сохранением пропорций и текущего воспроизведения. Внизу видео — запуск/пауза, время, перемотка, выключение звука и громкость самого плеера. Видео запускается только кнопкой ▶; смена кадра или сворачивание приложения приостанавливает воспроизведение.

Настройки подключения доступны через «Разделы → Настройки» или кнопку внизу бокового меню. Выбранная сортировка сохраняется; её статистика остаётся только в оперативной памяти. При недоступности рейтинга приложение явно переключается на А–Я; повторный выбор режима повторяет запрос. Личная история скачиваний в статистику не отправляется.

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

1. Скачай `QCatalog-0.6.3-preview.apk` из [Releases](https://github.com/RoyMatus/qcatalog-quest/releases/tag/v0.6.3-preview).
2. Подключи Quest по USB и разреши USB-отладку **внутри шлема**. Отображение «Внутреннего общего накопителя» в Проводнике означает доступ к файлам, но ещё не подтверждает разрешение отладки.
3. В PowerShell, открытом в папке Platform Tools, проверь устройства:

   ```powershell
   .\adb.exe devices -l
   ```

4. Найди именно свой Quest со статусом `device`. Если указано `unauthorized`, подтверди запрос в шлеме. В следующих командах замени `QUEST_SERIAL` на серийный номер своего Quest, а путь к APK — на путь скачанного файла:

   ```powershell
   .\adb.exe -s QUEST_SERIAL install -r 'C:\Downloads\QCatalog-0.6.3-preview.apk'
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

1. Открой карточку игры и нажми «Установить». Для уже установленной игры доступны «Обновить» или «Скачать APK». На этом шаге релиз ставится в очередь скачивания.
2. Дождись статуса «Готово к установке» во вкладке «Загрузки».
3. Выбери запись → «Установить». При первом запуске разреши QCatalog устанавливать приложения через предложенный системный экран, затем вернись и повтори установку.
4. Подтверди установку в системном диалоге Android.
5. После успешной установки и проверки данных задача и её установочные файлы удаляются автоматически. Открой игру из раздела «Библиотека» QCatalog или библиотеки Quest.

Если идентификатор пакета APK отличается от каталога, QCatalog покажет оба значения и запросит отдельное подтверждение. Оно действует только для конкретного файла с проверенным SHA-256. Несовпадение числового кода версии блокирует установку.

Для обновлений выбери «Обновить каталог» (в узком окне — «Разделы → Настройки → Обновить каталог»), затем открой раздел «Обновления». Карточка показывает установленную версию и версию каталога. Кнопка «Обновить» скачивает новый релиз, а сама установка по-прежнему требует системного подтверждения.

Перед установкой QCatalog проверяет доступные сведения о подписи. При явно разных ключах установленного приложения и APK он объясняет конфликт заранее. История ротации сертификатов учитывается; окончательную совместимость решает Android. Ошибки установщика показываются как «Ошибка установки» с причиной, а не как «Готово к установке, код 5». Повторная попытка использует уже скачанный APK.

Приложение не удаляет установленную игру автоматически. Для замены на сборку с другой подписью потребуется отдельно удалить старую версию; это может стереть сохранения и моды. Простое повторение установки или скачивания конфликт подписи не устраняет.

Проверка версий работает и для игр, установленных другими способами, если совпадает идентификатор пакета. Название игры и дата релиза не используются для определения новизны. Если Android-пакет отличается, автоматическое сопоставление возможно только для ранее подтверждённого расхождения. Удаление истории это сопоставление не сбрасывает.

### Релизы с install.txt и архивами 7z

Порты вроде Half-Life / XASH могут содержать APK, `Install.txt` и архив `.7z`. QCatalog распаковывает архив внутри скачанного релиза с проверкой CRC и путей, затем переводит известные `adb install` и `adb push` в системную установку APK и проверенное копирование данных. Для общей папки игры, например `/sdcard/XASH`, нужен «Доступ ко всем файлам». На распаковку и копирование требуется дополнительное свободное место.

Завершённая распаковка используется повторно; прерванная начинается заново из уже сохранённого архива, без его повторного скачивания. Старую ошибку о неподдерживаемом `install.txt` приложение при запуске переводит в готовую к установке задачу. Команды удаления старого APK и папок не запускаются: заменяются только поставляемые файлы, остальные остаются. Неизвестные инструкции показывают конкретную ошибку и сохраняют загрузку. Несколько APK и split APK пока не поддерживаются.

### 5. Освободить место и очистить историю

Во вкладке «Загрузки» → «Очистка файлов и истории»:

| Действие | Результат |
| --- | --- |
| Удалить скачанные файлы | Удаляются APK и прочие файлы релизов из собственной папки загрузок QCatalog. Установленные приложения и их сохранения остаются. |
| Очистить историю загрузок | Удаляются записи списка. Скачанные файлы остаются на месте. |

Для одной загрузки: запись → «Ещё» → нужное действие. Перед удалением появляется подтверждение. Общая очистка находит и файлы релизов, записи которых уже удалены из истории. Во время активных загрузок и установок очистка блокируется. После новой успешной установки/обновления установочные файлы автоматически удаляются, а задача исчезает из активных загрузок. Ошибка установки сохраняет файлы; ошибка очистки допускает отдельный повтор. Старые завершённые записи автоматически не очищаются.

### Установка APK с компьютера по сети

Нажми **«APK с компьютера»** слева или **Настройки → Установить APK с компьютера**. Выбери найденный компьютер либо введи его имя, локальный IPv4-адрес или путь `\\компьютер\папка`. Затем выбери общую папку, вложенную папку и APK.

Для защищённой папки введи имя пользователя Windows (при необходимости `КОМПЬЮТЕР\пользователь` или `ДОМЕН\пользователь`) и пароль. PIN Windows Hello не подходит. Пустой логин означает гостевой доступ, если он разрешён на компьютере. Пароль хранится только в памяти соединения; поле сразу очищается после подключения, буфер пароля очищается при закрытии.

APK копируется на шлем с прогрессом и возможностью отмены, затем проходит обычную проверку пакета, версии и совместимости подписи и передаётся системному установщику. Подтверди установку в окне Android. После успешной установки действуют обычные правила очистки загрузок. Для сетевых APK версия читается из самого файла, а не из каталога QLoader.

Компьютер и шлем должны находиться в одной локальной сети; общей папке нужно разрешение чтения. Если автопоиск ничего не нашёл, попробуй адрес вручную и проверь настройки общего доступа/брандмауэра компьютера. Поддерживаются SMB2/SMB3; доступ к файлам компьютера только на чтение. Автопоиск проверяет ближайший диапазон IPv4; компьютеры в других сегментах можно указать вручную. Для нестандартного порта допустим `имя:порт`.

Списки сетевых папок и реквизиты не записываются на диск. APK относится к установочным загрузкам и сохраняется для установки/повтора. Отменённая передача удаляет свой неполный файл; сетевой импорт после прерывания нужно выбрать заново, автоматическая докачка QLoader к нему не относится. Split-комплекты и дополнительные OBB из сетевой папки этим выбором не импортируются.
### Описания, перевод и медиа

Описание и ссылки карусели запрашиваются только при открытии игры. Русский текст источника показывается напрямую. Английское описание появляется сразу после получения; короткий фрагмент переводится через [MyMemory](https://mymemory.translated.net/doc/spec.php) и при успехе заменяет оригинал. Если перевод недоступен или пуст, английский остаётся без сообщений об ошибке перевода. Отдельной кнопки описания нет. Ссылки и email в описании нажимаются и открываются соответствующим приложением; адреса именованных HTML-ссылок сохраняются при очистке текста. Описания сокращаются до краткого текста; перевод может содержать ошибки.

Проверка публичного API QLoader 20 октября 2026 года с запросом русского языка вернула английские описания Beat Saber, Star Wars: Tales from the Galaxy’s Edge, Gorilla Tag и Demeo. Это выборка, а не проверка всего каталога; поле языков игры не означает наличие описания на этих языках.

MyMemory выбран как вариант без регистрации пользователя и ключа API. У анонимного доступа есть [лимит 5 000 символов в день на IP](https://mymemory.translated.net/doc/usagelimits.php). Официальный Google Cloud Translation — отдельный от бесплатного сайта Google Translate сервис: нужны проект, авторизация и включённый биллинг; для стандартного NMT-перевода первые 500 000 символов в месяц бесплатны, затем действует оплата. [Настройка Google](https://docs.cloud.google.com/translate/docs/setup), [тарифы](https://cloud.google.com/products/translate/pricing). Переключатель на Google в приложении не реализован. MyMemory не заявляется лучшим по качеству. Проверенного бесплатного публичного API без ограничений не найдено. [LibreTranslate на собственном сервере](https://docs.libretranslate.com/guides/installation/) позволяет не задавать квоты символов и запросов, но требует работающего сервера и его ресурсов; к приложению такой сервер сейчас не подключён.

Физическое управление Touch-стиком в обоих режимах подтверждено владельцем Quest 3S для версии 0.5.5.

Источники карусели: API QLoader, публичная страница Meta по идентификатору игры из QLoader, изображения в папке релиза и `.meta/videos/PACKAGE.mp4` на зеркале QLoader. Страница Meta позволяет получить свежие ссылки, когда ссылки из API устарели. Изображения и видео привязываются к выбранной игре, а не к рекомендациям магазина. У части игр медиа отсутствует, удалено или временно недоступно.

При открытии карточки запускается предзагрузка её изображений в RAM: обложка и до 16 изображений из источника. Два фоновых запроса работают параллельно; готовые кадры переключаются сразу из памяти, без повторной загрузки и декодирования. Пока начальная предзагрузка не завершилась, кадр ещё может загружаться. Кэш изображений одной карточки ограничен 64 МиБ или четвертью доступной Java-кучи, если она меньше; размер декодированных изображений подбирается под этот бюджет. Видео читается после запуска в отдельный RAM-буфер до 64 МиБ. При закрытии карточки сетевые запросы отменяются, изображения освобождаются, плеер и буфер видео закрываются; поздние результаты не восстанавливают кэш.

Описания, переводы, скриншоты и видео не имеют дискового кэша. Для обложек видимых карточек есть ограниченный кеш RAM 16 МиБ. При повторном открытии игры её сведения запрашиваются заново. Каталог, настройки, очередь/история и скачанные установочные файлы сохраняются отдельно. Конфиг и персональная ссылка QLoader не отправляются в Meta или переводчику.

### Ограничения и проверка

- Подтверждены получение каталога, скачивание на Quest 3S и установка одного обычного APK через Android. Запуск самой установленной игры в рамках проверки не проверялся.
- Для `install.txt` поддерживаются установка одного APK и копирование данных `adb push`, в том числе из архивов 7z. Пути и содержимое архива проверяются; команды оболочки не запускаются. Инструкции удаления старого APK/данных не выполняются: существующие сохранения остаются, файлы поставки заменяются проверенными копиями. Неизвестные команды показывают конкретную ошибку, скачанные файлы сохраняются. Несколько APK и split APK пока не поддерживаются.
- Копирование OBB реализовано, но успешная установка OBB на Quest 3S **не подтверждена**. По [документации Android](https://developer.android.com/training/data-storage/manage-all-files), доступ ко всем файлам не отменяет ограничения чужих каталогов `Android/obb`. При отказе записи QCatalog отдельно сообщает, что APK установлен, а данные не завершены.
- Для загрузок, начатых в 0.3.0, позиция внутри файла сохраняется на диск. После повторного открытия QCatalog или загрузки шлема очередь продолжается автоматически; ручная «Пауза» сохраняется и сама не отменяется. Полностью скачанные файлы повторно не загружаются. При обычной паузе сохраняется принятый байт; при аварийном выключении используется последний надёжный checkpoint, записываемый примерно каждые 4 МиБ. Если параметры исходного неполного файла изменились, он загружается заново. Частичные файлы старых версий не имеют этих checkpoints и могут потребовать нового скачивания.
- Автозапуск после включения зависит от системного события загрузки и доступности сети/разблокировки. После «Принудительно остановить» в настройках Android нужно снова открыть QCatalog. Сам шлем в ходе проверки не перезагружался, чтобы не прерывать пользовательскую очередь; обработчик события проверяется в отдельном тесте.
- Это ARM64 preview-сборка с `debuggable=true`, не магазинный production-релиз. Отладочный доступ нужен, в частности, для помощника переноса настроек. Не предоставляй доступ ADB постороннему компьютеру.
- Форматы и доступность серверов поставщика могут измениться. Все релизы каталога и другие модели Quest не проверены.
- Форматирование описаний проверено на синтетических данных; реальные скриншот и видео одной игры проверены на Quest 3S, включая паузу и перемотку. Массовая загрузка медиа и переводов не выполнялась. Проверки вёрстки не заменяют оценку удобства взаимодействия лучом в VR.

### Приватность

Персональная ссылка хранится с шифрованием Android Keystore в закрытой папке приложения. Конфиг хранится там же в формате поставщика: зашифрованный файл остаётся зашифрованным, но для импортированного открытого конфига дополнительное шифрование не добавляется. Резервное копирование Android отключено. Полные сетевые адреса скрываются в сообщениях об ошибках приложения. Шлем обращается к серверам поставщика, публичным страницам/CDN Meta для медиа и, при необходимости перевода, MyMemory; собственного сервера QCatalog нет.

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

Результат: `build/QCatalog-0.6.3-preview.apk`. Сборка проверяет подпись APK и по умолчанию запускает тесты каталога, безопасного удаления файлов и форматирования описаний. Исходники модифицированного бинарного движка поставщика в репозиторий не входят; этот движок не пересобирается из исходников.

Для обновления поверх установленного APK нужен тот же ключ подписи. `build.ps1` создаёт локальный `build/debug.keystore`, если его ещё нет. Сохрани свой ключ отдельно; он исключён из Git. Самостоятельная сборка с новым ключом не установится как обновление готового APK из Releases.

Проверки на Quest после сборки и установки APK, подключения конфига и получения каталога:

```powershell
.\test-quest.ps1 -Serial 'QUEST_SERIAL' `
  -JavaHome 'C:\Path\To\jdk-21' -SdkPath 'C:\Path\To\Android\Sdk'
```

Сценарий временно устанавливает тестовый пакет с тем же ключом и удаляет его после проверки. Он читает настоящий список установленных приложений, но проверяет удаление на отдельной временной папке; пользовательские загрузки не удаляет. Не запускай во время работы с QCatalog: instrumentation перезапускает процесс приложения. Описания и изображения тестом не запрашиваются.

`test-storage.ps1` принимает те же параметры и проверяет шифрование ссылки и пустую ссылку при импорте файла в отдельном тестовом приложении. Он не останавливает основной QCatalog и не обращается к его данным. Дополнительные параметры `-PreviousApk` и `-IncomingApk` позволяют проверить подписи двух локальных APK относительно установленного приложения: ожидается, что первый конфликтует, а второй имеет совместимую подпись. Тест не устанавливает эти APK и удаляет временные копии вместе со своим тестовым пакетом.

Сборка запускает `fetch-dependencies.ps1`: при первом запуске нужны Maven Central и сеть для библиотек 7z и SMB. Версии и SHA-256 закреплены в `dependencies.json`; библиотеки включаются в APK, поэтому на шлеме отдельно скачивать их не требуется. Лицензии включены в `assets/licenses/`.

### Проверки текущей версии

- В 0.6.0: 153 изолированные UI/cache-проверки, включая отдельные быстрые отклонения стика и секундный повтор; 16 проверок сетевых адресов и путей на ПК; 16 проверок на Quest настоящей SMB2-шары, окна выбора файла, передачи APK с совпадением SHA-256, отмены, очистки пароля и подготовки сессии Android. Тестовая сессия отменена без установки игры. Временная шара, сервер, папка и реквизиты после проверки удалены.
- 76 проверок хранения/очереди/медиа в 0.5.6, включая предзагрузку настоящей галереи до выбора кадра, повторное использование битмапа и видео; новые медиафайлы в приватном хранилище и дисковом кэше не появились.
- 151 изолированная UI-проверка в 0.5.6, включая предзагрузку всех кадров до выбора, повторное использование тех же изображений, бюджет RAM, отсутствие новых файлов и освобождение памяти/отмену поздних результатов при закрытии.
- 88 изолированных UI-проверок в 0.5.5: прокрутка Horizon OS в обоих размерах карусели, попадание указателя в медиа, секундный повтор и отсутствие самопроизвольного листания после отпускания, кликабельные ссылки и точные адреса запуска браузера. 11 проверок текста без сети. Источник схемы ввода: [Meta input adaptation](https://github.com/meta-quest/agentic-tools/blob/main/skills/hz-android-2d-porting/references/input-adaptation.md).
- 72 изолированные UI-проверки в 0.5.4, включая искусственно поданные события стиков/D-pad, секундный повтор и его отмену. Доставка событий от физического Touch-контроллера в 2D-окно требует проверки владельцем шлема.
- 58 изолированных UI-проверок в 0.5.3: карусель без подписей и счётчика, сохранённые жесты, увеличение и управление. 9 проверок форматирования и выбора русского/английского описания без сети.
- 67 изолированных UI-проверок в 0.5.2 на Quest 3S: карточки и темы, карусель и стрелки, увеличение/возврат по клику, отсутствие подложки и кнопки закрытия, громкость и выключение звука. На публичном видео проверены реальное увеличение, сохранение позиции и воспроизведения, возврат к исходному размеру, пауза и переключение кадра.
- 72 изолированные проверки хранения/очереди/медиа и 36 проверок установленного приложения в 0.5.0 подтвердили настоящий скриншот, видео именно с настроенного зеркала QLoader, воспроизведение, паузу, перемотку, отсутствие новых файлов медиа и сохранность очереди.
- Проверка скачанного Half-Life / XASH: 13 112 распакованных файлов, 4 368 536 271 байт, совпадение версии APK с каталогом. Эта проверка не устанавливала игру и не меняла её существующие данные.

`test-storage.ps1 -Ui` запускает изолированные проверки интерфейса; добавление `-LiveMedia` включает проверку реального публичного видео без личного конфига. `test-storage.ps1 -LiveMedia` проверяет медиа одной игры в отдельном приложении; `test-quest.ps1 -LiveMedia` использует установленное приложение и его зеркало. Эти скрипты принимают те же `-Serial`, `-JavaHome`, `-SdkPath`, что в примере выше. Сетевые проверки медиа включаются явно; без них описания, переводы и изображения не запрашиваются. Проверку установленного приложения запускай только при завершённых загрузках и установках.

Опция `test-quest.ps1 -SpecialReleaseId RELEASE_ID` распаковывает существующую загрузку и проверяет план установки внутри её папки. Распакованные файлы остаются для последующей установки; очередь и установленная игра не меняются. Это отличается от изолированных временных тестовых данных.

### Участие и доступ к репозиторию

Код и готовые релизы доступны для чтения и скачивания, issues открыты для сообщений об ошибках. Запись в этот репозиторий и выпуск релизов доступны владельцу. GitHub также позволяет форки и предложения через pull request; они сами по себе не дают права менять исходный репозиторий. Не публикуй персональные конфиги в обсуждениях.

---

## English

### What it does

QCatalog is a standalone Android app with a windowed interface for Meta Quest. Once installed and connected, the headset retrieves the catalogue, downloads releases and opens Android's package installer itself. A PC is not required for these everyday operations.

Anyone can install the same APK and supply **their own** compatible QLoader configuration. There is no username/password sign-in screen: connect using an HTTPS configuration URL or import an `FFA_config` file. The APK and repository contain no personal access URL, user configuration, account or game files.

This is an independent experimental client, not an official Meta, QLoader or dipVR product. Compatibility has been checked with the dipVR / VR Games RUS distribution of QLoader 1.2.0. Other providers have not been verified. The current app interface is in Russian; the instructions below include the corresponding button labels.

### Features

- Catalogue, installed library, downloads and updates; search by title, release or package.
- Each card shows its own release version and numeric code, for example `v1.40.8_7379 · код 1716`. Missing marketing versions are not invented.
- A–Z or QLoader download popularity over 1/7/30 days. This is download popularity, not Meta star ratings; missing values sort last.
- On-headset downloads, queue, pause and durable byte-offset resume after reopening the app or booting the headset.
- Android-confirmed APK installation with package, version and signing checks.
- Actual installed-version comparison; a green **«✓ Есть обновление»** marks newer catalogue versions in the library.
- Phase-specific download/APK/data-copy/verification progress, with an indeterminate indicator when Android provides no exact installation percentage.
- After successful installation/update and data verification, the task leaves active downloads and its installation files are removed. Failures retain files for retry.
- Known `install.txt` instructions and on-device 7z extraction; separate download-file and history cleanup.
- Russian summaries and a large cover/screenshot/video carousel. Media loads on demand without media files or disk cache.

### Interface

The app uses [Cards v1 resources](design/cards-v1/README.md). The main catalogue opens expanded at 1920 × 1080 dp in landscape, with five columns at 1920 dp or four at 1280 dp. Narrow layouts use the labelled Sections menu. System light/dark settings control surfaces, text, buttons and dialogs. Targets are at least 64 dp; action buttons align and stack when space is insufficient.

The game-details dialog is narrow: up to 720 dp, constrained by the available panel. The carousel displays one large frame without captions or counters. Translucent edge chevrons have no filled side panels; hover or focus reveals them, and leaving hides them. Images support horizontal swipes. Moving the stick left/right changes one frame immediately in expanded view or while pointing at media in the game card; each separate flick acts immediately and only continuous holding repeats once per second. Horizon OS delivers Touch stick input as ACTION_SCROLL / AXIS_HSCROLL; ordinary gamepad events are also supported. Centering the stick, losing focus, backgrounding the app or leaving expanded view stops repetition. Click an image or the video picture to expand it to near-panel size; click again to return. There are no separate enlarge/close buttons or backdrop. Video keeps its aspect ratio and playback position while resizing. Bottom controls provide play/pause, elapsed time, seeking, mute and player volume. Video starts only after pressing ▶ and pauses when changing frames or backgrounding the app.

Connection settings are under Sections → Settings or the sidebar Settings button. Only the selected sorting mode persists; statistics stay in RAM. Service failures explicitly fall back to A–Z, and reselecting a mode retries the request. No personal download reports are sent.

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

1. Download `QCatalog-0.6.3-preview.apk` from [Releases](https://github.com/RoyMatus/qcatalog-quest/releases/tag/v0.6.3-preview).
2. Connect the Quest over USB and allow USB debugging **inside the headset**. Seeing the headset's shared storage in Windows Explorer does not mean USB debugging has been authorized.
3. Open PowerShell in the Platform Tools directory and run:

   ```powershell
   .\adb.exe devices -l
   ```

4. Identify your Quest with the `device` status. For `unauthorized`, accept the debugging prompt inside the headset. Replace `QUEST_SERIAL` below with your Quest's serial, and adjust the APK path:

   ```powershell
   .\adb.exe -s QUEST_SERIAL install -r 'C:\Downloads\QCatalog-0.6.3-preview.apk'
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

1. Open a game and press **«Установить» (Install)**. Installed games offer **«Обновить» (Update)** or **«Скачать APK» (Download APK)**. This step queues the download.
2. Under **«Загрузки» (Downloads)**, wait for **«Готово к установке» (Ready to install)**.
3. Select the item and press **«Установить» (Install)**. On first use, allow QCatalog to install apps through the offered Android settings screen, return and try Install again.
4. Confirm the Android installation dialog.
5. After successful installation and data verification, the task and its installation files are removed automatically. Open the game from QCatalog Library or the Quest library.

If the APK's package identifier differs from the catalogue, QCatalog shows both and requires explicit confirmation tied to the SHA-256 of that exact APK. A different version code blocks installation.

For updates, press the top **«Обновить каталог» (Refresh catalogue)** button to retrieve the latest catalogue, then select **«Обновления» (Updates)**. The card shows installed and catalogue versions. Its **«Обновить» (Update)** button downloads the new release; installing it still requires Android confirmation.

Before installation QCatalog checks available signing information. Clearly unrelated signing keys produce an explanation before opening the installer. Certificate rotation histories are considered; Android remains the final authority. Installer failures appear as **«Ошибка установки» (Installation error)** with the reason, instead of a misleading ready-to-install status and code 5. A retry reuses the downloaded APK.

QCatalog does not automatically uninstall existing apps. Replacing an app with a differently signed build requires separately removing the old version, which may delete saves and mods. Simply retrying or downloading again does not resolve a signature mismatch.

Installed versions come from Android, including games installed by other tools, when package identifiers match. Updates compare numeric `versionCode`, not release dates or marketing version strings. Previously confirmed package aliases survive history cleanup.

### Releases with install.txt and 7z archives

Ports such as Half-Life / XASH may contain an APK, `Install.txt` and `.7z` data. QCatalog expands the archive within the downloaded release with CRC/path checks, then translates known `adb install` / `adb push` instructions into Android installation and verified data copying. Shared game folders such as `/sdcard/XASH` require All files access. Extraction and copying need additional free space.

Completed extraction is reused; interrupted extraction restarts from the saved archive without redownloading it. Old unsupported-install.txt errors become ready-to-install tasks on launch. Legacy uninstall/delete commands are not executed: only supplied files are replaced, and other files remain. Unknown instructions produce an explicit error and preserve downloads. Multiple APKs and split APK remain unsupported.

### 5. Delete downloads and clear history

Under **«Загрузки» → «Очистка файлов и истории» (File and history cleanup)**:

| Action | Effect |
| --- | --- |
| «Удалить скачанные файлы» / Delete downloaded files | Removes downloaded APKs and other release files from QCatalog's own downloads folder. Installed apps and their saves remain. |
| «Очистить историю загрузок» / Clear download history | Removes list entries while keeping downloaded files. |

For one item, open it → **«Ещё» (More)** → select the action. Cleanup asks for confirmation and is blocked during active downloads/installations. Bulk file cleanup also finds downloads whose history entries have already been removed. New successful installs/updates automatically delete their staging files and leave the active download list. Failed installs retain source files; failed cleanup has a separate retry. Legacy completed tasks are not automatically migrated into deletion.

### Install an APK from a computer over the network

Choose **«APK с компьютера»** in the sidebar, or **Settings → Install APK from computer**. Select a discovered computer or enter its hostname, local IPv4 address or UNC path `\\computer\share`. Browse the share and folders, then select an APK.

Protected shares require the Windows username (optionally `COMPUTER\user` or `DOMAIN\user`) and password, not the Windows Hello PIN. A blank username requests guest access if the server allows it. Credentials remain in connection memory only; the password field clears immediately and the password buffer is erased on close.

The APK copies to the headset with progress/cancellation, then uses the normal package/version/signature checks and Android installer confirmation. Successful installation uses the usual download cleanup. Network-APK versions come from the file itself, independently of the QLoader catalogue.

Both devices must be on the same local network and the share must allow reading. If discovery finds nothing, enter the address manually and check Windows sharing/firewall settings. SMB2/SMB3 are supported with read-only file access. Discovery checks the nearby IPv4 range; other segments can be entered manually. Custom ports use `hostname:port`.

Directory listings and credentials have no disk cache. The APK itself is an installation download retained for installation/retry. Cancellation deletes the incomplete transfer; interrupted network imports must be selected again and do not use QLoader automatic resume. This picker imports individual APKs, not split packages or additional OBB files.
### Descriptions, translation and media

Descriptions and carousel references load only when opening a game. Russian source text is displayed directly. English text appears as soon as fetched; a short fragment is translated using [MyMemory](https://mymemory.translated.net/doc/spec.php) and replaces the source on success. An unavailable or empty translation leaves English visible without translation-error notices. There is no separate description button. Web/email links are clickable and open in the corresponding application; named HTML link destinations survive text cleanup. Text is shortened; machine translation can be inaccurate.

A public QLoader API sample on 20 October 2026 returned English descriptions for Beat Saber, Star Wars: Tales from the Galaxy’s Edge, Gorilla Tag and Demeo despite requesting Russian. This does not establish the language of the entire catalogue; supported game languages do not imply localized descriptions.

MyMemory is the keyless option, with an anonymous [5,000-character daily allowance per IP](https://mymemory.translated.net/doc/usagelimits.php). Google Cloud Translation is separate from the free consumer Translate website: it requires a project, authentication and billing; standard NMT has a 500,000-character monthly free allowance followed by usage charges. [Google setup](https://docs.cloud.google.com/translate/docs/setup), [pricing](https://cloud.google.com/products/translate/pricing). The app does not currently offer a Google provider switch. MyMemory is not claimed to offer the best quality. No verified free, unlimited public API was found. [Self-hosted LibreTranslate](https://docs.libretranslate.com/guides/installation/) can run without character/request quotas, but requires an operating server and its resources; no such server is currently connected to this app.

The Quest 3S owner confirmed physical Touch-stick navigation in both layouts with version 0.5.5.

Carousel sources are the QLoader API, the public Meta page identified by QLoader, images in the release directory and the mirror's `.meta/videos/PACKAGE.mp4`. The Meta page supplies fresh links when API links expire. Structured references are scoped to the chosen game, excluding store recommendations. Media can be missing, delisted or temporarily unavailable.

Opening game details preloads its cover and up to 16 source images into RAM with two background workers. Ready frames switch directly from decoded memory without repeat downloads or decoding; frames can still be loading during the initial prefetch. Each card image cache is bounded by 64 MiB or one quarter of the Java heap, whichever is smaller; decoded image size fits that budget. Video loads after playback starts into a separate RAM buffer of up to 64 MiB. Closing details cancels requests, releases decoded images, closes the player/video buffer and discards late results.

Descriptions, translations, screenshots and video have no disk cache. Visible grid covers use a bounded 16 MiB RAM cache. Reopening a game requests its details again. The catalogue, settings, queue/history and downloaded installation files persist separately. QLoader credentials and access URLs are never sent to Meta or translation services.

### Limitations, validation and privacy

- Catalogue retrieval, direct downloading and installation of one ordinary APK were verified on Quest 3S. The installed game's own runtime behavior was not tested.
- `install.txt` supports one APK and declarative `adb push` data placement, including 7z archives. Archive entries and paths are validated; no shell is launched. Legacy uninstall/delete instructions are not executed: existing saves remain and supplied files are replaced with verified copies. Unknown commands produce an explicit error while keeping downloads. Multiple APKs and split APK remain unsupported.
- OBB copying is implemented but successful OBB installation on Quest 3S is **unverified**. [Android's all-files access](https://developer.android.com/training/data-storage/manage-all-files) does not remove restrictions on other apps' `Android/obb` directories. A data-copy failure is reported separately from APK installation.
- Downloads started with 0.3.0 persist a byte checkpoint. Reopening QCatalog or receiving the headset boot event restores pending work automatically; a manual pause remains paused. Completed files are reused. Normal pause saves the received byte position; abrupt power loss resumes from the last durable checkpoint, written roughly every 4 MiB. A changed partial-file identity restarts that file. Older versions' partial files lack these checkpoints and may need downloading again.
- Boot restart depends on Android delivering the boot event and network/unlock availability. After Android's Force stop action, reopen QCatalog first. The headset was not rebooted during validation to preserve the user's active queue; boot handling is tested in isolation.
- This is an ARM64 preview with `debuggable=true`, not a production store build. Debug access is used by the provisioning helper. Do not authorize ADB access for an untrusted computer.
- Provider formats and server availability can change. Compatibility with every catalogue release or other Quest models has not been established.
- Description formatting uses synthetic tests; a real screenshot and video were checked on Quest 3S, including pause and seeking. No catalogue-wide media or translation prefetch was performed. Layout checks do not establish subjective comfort with VR pointer interaction.

The access URL is encrypted with Android Keystore in the app's private directory. The configuration is kept there in the provider's format: encrypted input remains encrypted, but an imported plaintext configuration does not gain extra encryption. Android backup is disabled. App error messages hide full network URLs. The headset contacts the provider, public Meta pages/CDNs for media and, when translation is needed, MyMemory; QCatalog has no separate backend.

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

Output: `build/QCatalog-0.6.3-preview.apk`. Pass `-SkipTests` to build without tests. The build verifies the APK signature and by default runs catalogue, download-containment/deletion and description-formatting tests. The provider's modified native engine source is not included or rebuilt here.

The build creates a local `build/debug.keystore` when absent. Keep your signing key privately: it is excluded from Git, and updates require the same key. Your own rebuild with a new key cannot update the APK distributed in Releases.

After building and installing the APK, connecting a config and fetching the catalogue:

```powershell
.\test-quest.ps1 -Serial 'QUEST_SERIAL' `
  -JavaHome 'C:\Path\To\jdk-21' -SdkPath 'C:\Path\To\Android\Sdk'
```

This temporarily installs a test package with the same signing key and removes it afterwards. It reads actual installed versions, but tests file deletion using isolated fixtures and preserves user downloads/history. Run it only while idle: instrumentation restarts the app process. Tests do not request descriptions, translations or images.

`test-storage.ps1` takes the same parameters and checks URL encryption and the empty URL used by file imports in a separate test app. It neither stops the real QCatalog app nor accesses its data. Optional `-PreviousApk` and `-IncomingApk` parameters check two local APKs against the installed app: the first is expected to conflict, and the second to have a compatible certificate. These APKs are not installed; the test package and its temporary copies are removed afterwards.

The build runs `fetch-dependencies.ps1`; its first run requires network/Maven Central access for the 7z libraries. Versions and SHA-256 values are pinned in `dependencies.json`. Libraries are embedded in the APK, with licenses under `assets/licenses/`, so headset extraction needs no separate library download.

### Current validation

- Version 0.6.0: 153 isolated UI/cache checks including quick separate stick flicks and held repeats; 16 host address/path checks; 16 Quest checks against a real SMB2 fixture share, picker UI, byte-identical APK transfer, cancellation, password cleanup and Android installer-session staging. The test session was abandoned without installing a game. Temporary share/server, folder and credentials were removed afterwards.
- 76 storage/queue/media checks in 0.5.6 include real-gallery prefetch before selection, bitmap reuse and video; no new media files appeared in private storage or disk cache.
- 151 isolated UI checks in 0.5.6 include all-frame prefetch before selection, identical bitmap reuse, RAM bounds, no new files, close-time memory release and discarded late results.
- 88 isolated UI checks in 0.5.5 cover Horizon OS scroll routing in both carousel sizes, pointer hit testing, one-second repeat with no runaway after release, clickable links and exact browser targets. Eleven offline text checks passed. Input mapping reference: [Meta input adaptation](https://github.com/meta-quest/agentic-tools/blob/main/skills/hz-android-2d-porting/references/input-adaptation.md).
- 72 isolated UI checks in 0.5.4 include injected stick/D-pad input, timed repetition and cancellation. Physical Touch-controller event delivery into the 2D window still requires confirmation by the headset owner.
- 58 isolated UI checks in 0.5.3 verify the caption/counter-free carousel, retained gestures, expansion and controls. Nine offline description checks cover formatting and Russian/English selection.
- 67 isolated UI checks in 0.5.2 on Quest 3S cover cards/themes, carousel/arrows, click-to-expand/restore without a backdrop or close button, volume and mute. A public video verifies actual enlargement, retained playback/position, restored compact size, pause and frame switching.
- 72 isolated storage/queue/media checks and 36 installed-app checks in 0.5.0 verified a real screenshot and configured-mirror video, playback, pause, seeking, no added media files and unchanged queue.
- An existing Half-Life / XASH release expanded into 13,112 files (4,368,536,271 bytes), with a matching APK catalogue version. Validation did not install the game or change existing game data.

`test-storage.ps1 -Ui` runs isolated UI checks; adding `-LiveMedia` checks a real public video without personal configuration. `test-storage.ps1 -LiveMedia` checks one game's media in a separate app; `test-quest.ps1 -LiveMedia` uses the installed app and configured mirror. Supply the same `-Serial`, `-JavaHome`, `-SdkPath` arguments as above. Network media checks are opt-in; without them, descriptions, translations and images are not requested. Run installed-app checks only while downloads and installations are idle.

Optional `test-quest.ps1 -SpecialReleaseId RELEASE_ID` expands an existing download inside its staging folder and validates its installation plan. Expanded files remain for later installation; the queue and installed game stay unchanged. This opt-in check is distinct from temporary isolated fixtures.

### Repository access and issues

Source and releases are public to read and download; issues are open for bug reports. Repository writes and releases are controlled by the owner. GitHub also permits forks and pull-request proposals, which do not grant write access to this repository. Include the app version, headset model, reproduction steps and sanitized error text in reports; never attach personal configuration files.
