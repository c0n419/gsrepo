version = 2

cloudstream {
    authors = listOf("csprofesor")
    language    = "tr"
    description = "Ddizi, dizi izle, dizi seyret, ddizi.tel - Güncel ve eski dizileri en iyi görüntü kalitesiyle bulabileceğiniz dizi izleme sitesi."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("TvSeries")
    iconUrl = "https://www.google.com/s2/favicons?domain=ddizi.tel&sz=%size%"
}

android {
    buildFeatures {
        buildConfig = true
    }
}

// Sync trigger: keep Feroxx provider synchronization active.