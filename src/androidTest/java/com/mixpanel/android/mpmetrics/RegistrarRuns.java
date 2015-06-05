package com.mixpanel.android.mpmetrics;


import android.util.Pair;

import com.mixpanel.android.mpmetrics.TweakRegistrar;
import com.mixpanel.android.mpmetrics.Tweaks;

import java.util.ArrayList;
import java.util.List;

public class RegistrarRuns implements TweakRegistrar {

    public RegistrarRuns() {
        declareWasCalled = false;
        wasRegistered = new ArrayList<Pair<Tweaks, Object>>();
    }

    @Override
    public void declareTweaks(final Tweaks t) {
        declareWasCalled = true;
    }

    @Override
    public void registerObjectForTweaks(final Tweaks t, final Object registrant) {
        wasRegistered.add(new Pair<Tweaks,Object>(t, registrant));
    }

    public boolean declareWasCalled;
    public List<Pair<Tweaks, Object>> wasRegistered;
}
