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

17. *Testing → Closed testing → Create track.* Add testers by email list (or a
    Google Group), and send them the opt-in link. A personal account created
    after November 2023 must run a closed test with **at least 12 testers,
    opted in for 14 days in a row**, before it may apply for production. The
    clock only starts once they have opted in, so gather the twelve first.
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
    changed. Problem reports that arrived by email are the honest answer to the
    last two.

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
> refreshed every six hours.
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

- **App icon** — 512×512 PNG, 32-bit. From the supplied picture the launcher
  icon is made from, the same way; still to be made.
- **Feature graphic** — 1024×500, JPEG or 24-bit PNG, no transparency. Still to
  be made, from the same picture and the icon's blues.
- **Phone screenshots** — 2 to 8. Each side between 320 and 3,840 pixels, and
  the long side no more than twice the short one — so the emulator's
  1080×2400 does not qualify, and they have to be taken at 1080×1920. Planned:
  the home screen, the AVVE sign with its labels, the calculator in the middle
  of a sum, and a receipt in receipt mode. Still to be taken.
