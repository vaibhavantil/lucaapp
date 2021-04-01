# Luca Android App

[luca](https://luca-app.de) ensures a data protection-compliant, decentralized encryption of your data, undertakes the obligation to record contact data for events and gastronomy, relieves the health authorities through digital, lean, and integrated processes to enable efficient and complete tracing.

This repository contains the source code of the luca app for Android. Note that the version that you get from the [Google Play Store](https://play.google.com/store/apps/details?id=de.culture4life.luca) may not always match with the `master` branch of this repository, e.g. because of a staged rollout or a [public beta](https://play.google.com/apps/testing/de.culture4life.luca) release.

## Changelog

An overview of all releases can be found [here](https://gitlab.com/lucaapp/android/-/blob/master/CHANGELOG.md).

## Development

This repository contains a common [Android Studio](https://developer.android.com/studio) project, which you can build without any special requirements.

1. Install the latest stable release of Android Studio
2. Checkout this repository and open the project
3. Build and deploy the `app` module

Note that `debug` builds may behave differently than `release` builds. They will, for instance, use different API endpoints to not mess with the production environment.

## Issues & Support

Please [create an issue](https://gitlab.com/lucaapp/android/-/issues) for suggestions or problems related to this app. For general questions, please check out our [FAQ](https://www.luca-app.de/faq/) or contact our support team at [hello@luca-app.de](mailto:hello@luca-app.de).

## License

See [license file](https://gitlab.com/lucaapp/android/-/blob/master/LICENSE).
