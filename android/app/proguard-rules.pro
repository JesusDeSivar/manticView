# kotlinx.serialization: keep generated serializers for our models.
-keepclassmembers class dev.jesusdesivar.manticwidget.data.** {
    *** Companion;
}
-keepclasseswithmembers class dev.jesusdesivar.manticwidget.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
