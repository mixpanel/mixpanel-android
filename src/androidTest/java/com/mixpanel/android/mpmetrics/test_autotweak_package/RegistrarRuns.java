package com.mixpanel.android.mpmetrics.test_autotweak_package;


import android.util.Pair;

import com.mixpanel.android.mpmetrics.Tweaks;

import java.util.ArrayList;
import java.util.List;

public class RegistrarRuns implements Tweaks.TweakRegistrar {

    public RegistrarRuns() {
        wasRegistered = new ArrayList<Pair<Tweaks, Object>>();
    }

    @Override
    public void registerObjectForTweaks(final Tweaks t, final Object registrant) {
        wasRegistered.add(new Pair<Tweaks,Object>(t, registrant));
    }

    public List<Pair<Tweaks, Object>> wasRegistered;

    public static final RegistrarRuns TWEAK_REGISTRAR = new RegistrarRuns();
}
