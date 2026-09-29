# Google Play submission notes

Everything the Play Console asks for, written out so a submission (or an update,
or an answer to a rejection) is copy-paste rather than improvisation. The Chrome
Web Store has its own, [STORE.md](STORE.md).

The upload is the App Bundle a release attaches:
`how-much-android-<version>.aab` on the
[releases page](https://github.com/belyakovvitaly/how-much-converter/releases),
under a tag beginning `android-`. CI builds it signed with the upload key — see
"Signing" in [android/README.md](android/README.md). Every upload needs a
`versionCode` higher than the last one Play has seen, so an upload is always a
new release, never a rebuilt old one.

What the console demands has changed before and will again. Where this file
states a rule — a limit, a waiting period — it is what held when it was
written; the console is the authority.

## Walkthrough

Steps only the account holder can take are marked **(you)** — they involve
signing in, paying, proving an identity, or making a legal declaration.
Everything else is prepared below.

**The account** — <https://play.google.com/console/signup>

1. **(you)** Pick the Google account to publish from, with 2-Step Verification
   on. It becomes the account's owner and is hard to change later.
2. **(you)** Choose a **personal** account, pay the one-time $25, and complete
   identity verification: a government ID, and a legal name and address that
   match it.
3. **(you)** Verify a contact phone number, and prove access to an Android
   phone by signing in to the Play Console app on it — the S25 will do.
4. **(you)** Decide the **public contact email** before the listing asks for it.
   Play shows it on the app's page, to everyone. The address problem reports
   go to is deliberately not in this repository or on any screen of the app;
   use a different one here unless you are content for that one to be public.

**The app** — *Create app*

5. Name `How Much? Price Converter`, default language **English (United
   States)**, **App**, **Free**. A free app cannot later become paid.
6. **(you)** Accept the Developer Program Policies and the US export laws
   declaration.

**Set up your app** — the dashboard's checklist, in its order

7. *Privacy policy*:
   <https://github.com/belyakovvitaly/how-much-converter/blob/main/PRIVACY.md>
8. *App access*: **All functionality is available without special access** —
   there is no account and nothing is locked.
9. *Ads*: **No, my app does not contain ads.**
10. *Content rating*: the questionnaire, answered below.
11. *Target audience and content*: below.
12. *News apps*: **No.**
13. *Data safety*: below — read that section before answering, it holds one
    decision.
14. *Government apps*: **No.** *Financial features*: **My app doesn't provide
    any financial features** — converting a displayed price is not a financial
    service. *Health*: **No health features.**
15. *Store listing*: the texts below, and the graphics under Assets.
16. *Store settings*: category **Travel & Local**; contact email from step 4;
    website `https://github.com/belyakovvitaly/how-much-converter`.

**Testing, then production**

17. *Testing → Closed testing → Create track.* A personal account created
    after November 2023 must run a closed test with **at least 12 testers,
    opted in for 14 days in a row**, before it may apply for production. How
    to find them, what to send them and what to ask of them is its own section,
    [Closed test](#closed-test), below.
18. *Create new release* on that track and upload the `.aab`. The first upload
    asks about **Play App Signing**: keep the default, *Let Google manage and
    protect your app signing key*. Play then re-signs what it delivers, and the
    project's key stays an upload key only. Consequence worth knowing: a copy
    installed from GitHub and a copy installed from Play are signed differently,
    so a phone cannot switch between them without uninstalling.
19. Release notes: one line from the release's commit is enough.
20. **(you)** Roll out to the closed track. The first review can take several
    days.
21. **(you)** After the 14 days: *Apply for production*. The console asks how
    the test went — how testers were recruited, what they reported, what
    changed. Drafts are at the end of [Closed test](#closed-test).

## Closed test

The rule, as it stood when this was written: at least **12 testers**, each with
a Google account on an Android phone, **opted in** to the closed track and
having **installed the app from Play** — an APK from GitHub does not count —
for **14 days in a row**. The clock runs while twelve are opted in at once; if
someone leaves and the count drops below twelve, it may start over. Nobody has
to use the app every day, but the application for production asks what testers
did and said, and a test with nothing to show for it is refused — and costs
another fourteen days.

**Aim for 15 to 20**, so a couple of people dropping out does not reset the
clock.

### Where testers come from

1. **People who would use it.** Friends, family, colleagues with Android phones —
   and above all anyone who is abroad, or often is. Someone who photographs a
   real shelf in a real shop sends the reports that make the production
   application easy to answer.
2. **Mutual-testing communities** — r/AndroidClosedTesting and
   r/TestersCommunity on Reddit, among others: test theirs, they test yours. It
   fills the numbers; the feedback tends to be thin.
3. **Paid "12 testers" services** — not recommended. Play sees how testers
   actually used the app, and a test bought for its numbers is the kind that
   gets refused.

Keep the list of who was asked, who joined and who reported what **outside the
repository**: it is people's names and email addresses, and the repository is
public.

### Setting it up

1. **(you)** Create a Google Group, e.g. `how-much-testers@googlegroups.com`,
   that anyone with the link can join. People are then added by joining it,
   not one by one in the console.
2. In the closed track's *Testers* tab: add the group, and a feedback channel —
   the report email, or the GitHub issues page.
3. Once the first release on the track has passed review, the tab shows the
   opt-in link:
   `https://play.google.com/apps/testing/io.github.belyakovvitaly.howmuch`
4. **(you)** Send each tester the invitation below — the group, the opt-in link,
   and the app's page:
   `https://play.google.com/store/apps/details?id=io.github.belyakovvitaly.howmuch`

What a tester has to do, in order: join the group with the Google account their
phone uses; open the opt-in link and press *Become a tester*; install from the
app's page on Play; and keep it installed for two weeks. An iPhone cannot take
part.

### The invitation

The app speaks only English; the invitations do not have to. The Spanish uses
Argentina's *vos*.

**Русский**

> Привет! Я сделал приложение How Much? — оно пересчитывает цены с фото в
> твою валюту: фотографируешь ценник, вывеску или чек, и поверх каждой цены
> появляется сумма в твоих деньгах. Есть и простой калькулятор валют.
>
> Чтобы выпустить его в Google Play, нужно две недели закрытого теста, и мне
> не хватает тестировщиков. Поможешь? Нужен Android и Google-аккаунт:
>
> 1. Вступи в группу: [ссылка на группу] — тем же аккаунтом, что на телефоне.
> 2. Открой ссылку и нажми кнопку, чтобы стать тестировщиком: [ссылка на тест]
> 3. Установи приложение из Google Play: [ссылка на приложение]
> 4. Не удаляй его 14 дней — это главное.
>
> А если будет минутка: сфотографируй пару ценников в магазине или меню в
> кафе. Если цена прочиталась неверно или не прочиталась — нажми флажок в
> углу экрана и «Send», отчёт с фото придёт мне на почту. Спасибо!

**Español**

> ¡Hola! Hice una app, How Much?, que convierte los precios de una foto a tu
> moneda: le sacás una foto a una etiqueta, un cartel o un ticket, y sobre cada
> precio aparece el monto en tu moneda. También trae una calculadora de
> monedas.
>
> Para publicarla en Google Play necesito dos semanas de prueba cerrada, y me
> faltan testers. ¿Me das una mano? Necesitás un Android y una cuenta de
> Google:
>
> 1. Unite al grupo: [enlace al grupo] — con la misma cuenta que usás en el
>    teléfono.
> 2. Abrí este enlace y tocá el botón para sumarte como tester: [enlace a la
>    prueba]
> 3. Instalá la app desde Google Play: [enlace a la app]
> 4. No la desinstales durante 14 días — es lo más importante.
>
> Y si tenés un minuto: sacale una foto a un par de precios en el súper o al
> menú de un bar. Si un precio sale mal o no sale, tocá la banderita en la
> esquina y «Send»: me llega un reporte con la foto por mail. ¡Gracias!

**English**

> Hi! I made an app, How Much?, that converts the prices in a photo into your
> currency: take a picture of a price tag, a sign or a receipt, and each price
> gets its amount in your money drawn over it. There is a currency calculator
> too.
>
> To publish it on Google Play I need two weeks of closed testing, and I am
> short of testers. Could you help? You need an Android phone and a Google
> account:
>
> 1. Join the group: [group link] — with the account your phone uses.
> 2. Open this link and press "Become a tester": [opt-in link]
> 3. Install the app from Google Play: [app link]
> 4. Keep it installed for 14 days — that is the part that matters.
>
> And if you have a minute: photograph a couple of price tags in a shop, or a
> menu. If a price comes out wrong, or not at all, tap the flag in the corner
> and "Send" — a report with the photo comes to me by email. Thank you!

### The fourteen days

- **Day 0** — the release passes review, the opt-in link works, invitations go
  out. The clock has not started.
- **Until twelve are in** — the *Testers* tab counts who has opted in. Chase the
  ones who joined the group but never pressed *Become a tester*: that is the
  step people miss.
- **Around day 3, 7 and 11** — one short message to everyone with one small
  thing to try: a receipt with receipt mode on; a price in another currency;
  the calculator with a sum. It keeps people from uninstalling, and each one
  that turns into a report is an answer for the application.
- **Every report** — note what it was and whether it was fixed. A fix released
  during the test goes to the same closed track with a new `versionCode`;
  testers get it as an ordinary update.
- **Day 14, with twelve still in** — *Apply for production*.

### The application for production

The console asks, in its own words, questions along these lines. Drafts, with
the parts only the test can supply in brackets — fill them from the notes
above, and do not send a claim the test did not bear out.

**How did you recruit testers? How easy was it?**

> Friends, family and colleagues, most of them people who travel or live
> abroad — the situation the app is for — [and N through a mutual-testing
> community]. Recruiting [was straightforward / took about N days], mainly
> because [reason].

**How did testers engage with the app?**

> Testers photographed price tags, shelves, menus and receipts in shops in
> [countries], used the currency calculator, and sent [N] problem reports
> through the app's Report button, each with the photo and what the app read
> from it.

**Summarize the feedback.**

> [The misreadings reported: e.g. small prices on a crowded shelf, a symbol
> missed on a handwritten sign.] [Anything said about the interface.]

**What did you change as a result?**

> [For each fix: what was misread, what changed, and the version that shipped
> it — e.g. "0.7.3: prices under a line of fine print are read; the tester's
> photo is now a regression test."]

**Who is the app for, and what does it give them?**

> People shopping in a country whose currency they do not think in. The app
> reads prices from a photo on the phone itself and draws the converted amount
> over each price, so there is nothing to type and nothing to match up; a
> calculator covers amounts that are not written down. It has no account, no
> ads and no tracking, and when it cannot tell which currency a "$" means, it
> leaves the price alone rather than show a wrong conversion.

**How did you decide it was ready?**

> [The reports in the last days of the test were N, all of them fixed or
> understood], the app was used on [N] different phones without a crash, and
> every fix made during the test shipped to testers as an update before
> applying.

## Listing

**App name** (30 characters) — How Much? Price Converter

**Short description** (80 characters)

> Photograph a price tag or a receipt and see it in your own currency.

**Full description** (4,000 characters)

> You are standing in a shop abroad, and the prices are in a currency you do
> not think in. Take a photo, and How Much? writes every price in your own
> currency, right on top of it.
>
> • Point, shoot, read. Photograph a price tag, a shelf, a menu or a street
> sign: each price the app recognizes gets its conversion drawn over it. Pinch
> to zoom into a crowded shelf.
> • From the gallery, too. Choose a photo you already took, or share one to
> How Much? from any other app.
> • Receipts. Receipt mode reads the amounts a shop prints with no currency
> beside them — discounts included, minus sign and all.
> • A calculator. For a price that is not written down anywhere: type it, add
> or multiply, and see it in the other currency. Either side can be typed into.
> • Knows where you are without asking. The currency of the prices is guessed
> from the country your phone's network is in, and yours from your SIM card —
> no location permission, no GPS. Change either with a tap.
> • Careful with "$". Half of Latin America writes "$" and means a peso. The
> app works out which one from where you are, and when it cannot tell, it
> leaves a price alone rather than guess: a wrong conversion is worse than
> none.
> • Reads on your phone. The text recognition runs on the device — nothing is
> uploaded to read a picture, and it works offline once the exchange rates
> have been fetched. Rates come from a public exchange-rate service and are
> updated once a day.
> • Keep it. Save the converted picture to your gallery, labels and all.
> • Tell us when it is wrong. From any picture, Report prepares a message with
> the photo and what the app read from it, and opens your mail app; nothing is
> sent until you send it.
>
> No account, no ads, no analytics, no tracking.

**Category** — Travel & Local. **Tags** — choose from the console's list;
"Travel", "Currency converter" and "Shopping" where offered.

## Content rating

Category: **Utility, Productivity, Communication, or Other.** Every content
question — violence, fear, sexuality, language, controlled substances, crude
humour, gambling — is **No**. Then:

- Does the app allow users to interact or exchange content with other users?
  **No.** A report goes through the phone's own mail app, to the developer
  only; no user sees another's content.
- Does the app share the user's current physical location with other users?
  **No.**
- Does the app allow users to purchase digital goods? **No.**
- Does the app provide unrestricted access to the internet? **No.**

Expected result: Everyone / PEGI 3 / USK 0.

## Target audience and content

- Target age groups: **18 and over.** The app is for travellers and shoppers;
  naming younger groups adds nothing for them and brings the Families policy's
  extra requirements with it.
- Could the app unintentionally appeal to children? **No.**

## Data safety

Play's definition: data is *collected* when it is transmitted off the device,
and *shared* when it goes to a third party. The app sends nothing by itself
beyond the rate request, which carries nothing about the user. A problem report
is the one question: the app prepares it, and the user sends it to the
developer from their own mail app.

**Recommended: declare the report.** It is the developer who receives it, and
declaring optional, user-initiated collection costs one line on the listing,
where leaving it out is the kind of omission a review rejects. Answers:

- Does your app collect or share any of the required user data types? **Yes.**
- Is all of the user data collected by your app encrypted in transit? **Yes** —
  a mail app sends over TLS.
- Do you provide a way for users to request that their data is deleted?
  **Yes** — by replying to the report or through the issues page, as
  PRIVACY.md says.

Data types, each **collected, not shared, optional** (users can choose whether
it is collected), and **not processed ephemerally**:

| Data type | Why it is in a report | Purpose |
| --- | --- | --- |
| Photos and videos → Photos | The picture that was misread | Analytics |
| App activity → Other user-generated content | What the user wrote about the problem | Analytics |
| App info and performance → Diagnostics | What the app read, the currencies, the app, Android version and phone model | Analytics |

Play's "Analytics" covers diagnosing and fixing bugs, which is what a report is
for. Not declared, because a report does not hold them: location (GPS tags are
stripped from the picture, and the currencies are guessed on the phone), any
identifier, contacts, and personal info. The user's own email address reaches
the developer as the sender of the message — that is the mail app's doing, not
the app's; if a reviewer disagrees, add Personal info → Email address,
collected, optional, for Developer communications.

**The alternative**, if a reviewer says a user-initiated email is not
collection by the app: **No** to the first question, and nothing to declare.
Answer that only when asked to.

## Assets

All in [`docs/play/`](docs/play), ready to upload.

- **App icon** — [`icon-512.png`](docs/play/icon-512.png), 512×512, 32-bit
  PNG. The owner's picture, [`tools/app-icon-source.png`](tools/app-icon-source.png),
  arranged as the launcher shows it: filling the square, its own rounded
  corners cut away with the icon's blues behind. Play rounds the corners
  itself. The source is 398×392, so this is it enlarged by about a third and a
  little softer than a native 512 — a larger original of the same picture would
  fix that, and nothing else would.
- **Feature graphic** — [`feature-graphic.jpg`](docs/play/feature-graphic.jpg),
  1024×500, JPEG (no transparency, as Play asks): the same picture beside the
  name, a one-line promise and the three ways in, on the icon's blues.
- Both are rendered by [`tools/play-graphics.sh`](tools/play-graphics.sh) from
  headless Chrome; run it again after changing either.
- **Phone screenshots** — 1080×1920, 9:16, from the release build (0.7.3; the
  receipt 0.7.4) on an emulator
  whose display was set to that size, with the status bar in demo mode (12:00,
  full battery, Wi-Fi, no notifications). Prices in ARS, converted to USD. In
  the order to upload:
  1. [`screenshot-1-sign.png`](docs/play/screenshot-1-sign.png) — a street sign
     in Buenos Aires, all seven prices converted in place.
  2. [`screenshot-2-receipt.png`](docs/play/screenshot-2-receipt.png) — a
     supermarket receipt in receipt mode, thirteen amounts and the discounts
     with their minus. The card's last digits were blanked before it ever
     reached the repository.
  3. [`screenshot-3-calculator.png`](docs/play/screenshot-3-calculator.png) —
     the calculator in the middle of 5,600 × 2.
  4. [`screenshot-4-home.png`](docs/play/screenshot-4-home.png) — the home
     screen.

  To take them again:

  ```sh
  adb shell wm size 1080x1920
  adb shell settings put global sysui_demo_allowed 1
  adb shell am broadcast -a com.android.systemui.demo -e command enter
  adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200
  adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
  adb exec-out screencap -p > screenshot.png
  # afterwards
  adb shell am broadcast -a com.android.systemui.demo -e command exit
  adb shell wm size reset
  ```
