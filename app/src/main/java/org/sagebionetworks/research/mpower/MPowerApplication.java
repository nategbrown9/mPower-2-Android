package org.sagebionetworks.research.mpower;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.Fragment;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.ActivityTransitionRequest;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import org.sagebionetworks.research.mpower.background.ActivityTransitionReceiver;
import org.sagebionetworks.researchstack.backbone.ResearchStack;
import org.sagebionetworks.bridge.android.di.BridgeStudyComponent;
import org.sagebionetworks.research.mpower.inject.DaggerMPowerApplicationComponent;
import org.sagebionetworks.research.mpower.inject.DaggerMPowerUserScopeComponent;
import org.sagebionetworks.research.mpower.inject.MPowerUserScopeComponent;
import org.sagebionetworks.research.sageresearch.BridgeSageResearchApp;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.android.AndroidInjector;
import dagger.android.DispatchingAndroidInjector;
import dagger.android.HasActivityInjector;
import dagger.android.HasServiceInjector;
import dagger.android.support.DaggerApplication;
import dagger.android.support.HasSupportFragmentInjector;

public class MPowerApplication extends BridgeSageResearchApp implements HasSupportFragmentInjector,
        HasActivityInjector, HasServiceInjector {
    @Inject
    DispatchingAndroidInjector<Activity> dispatchingActivityInjector;

    @Inject
    DispatchingAndroidInjector<Fragment> dispatchingSupportFragmentInjector;

    @Inject
    DispatchingAndroidInjector<Service> dispatchingServiceInjector;

    // this causes ResearchStack provider method, which also initializes RS, to be called during onCreate
    @Inject
    ResearchStack researchStack;

    @VisibleForTesting
    @Override
    protected AndroidInjector<? extends DaggerApplication> applicationInjector() {
        return DaggerMPowerApplicationComponent
                .builder()
                .mPowerUserScopeComponent((MPowerUserScopeComponent) getOrInitBridgeManagerProvider())
                .application(this)
                .build();
    }

    @Override
    protected MPowerUserScopeComponent initBridgeManagerScopedComponent(BridgeStudyComponent bridgeStudyComponent) {
        MPowerUserScopeComponent bridgeManagerProvider = DaggerMPowerUserScopeComponent.builder()
                .applicationContext(this.getApplicationContext())
                .bridgeStudyComponent(bridgeStudyComponent)
                .build();
        return bridgeManagerProvider;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        addActivityTransitionReceiver(this);

    }

    public static void addActivityTransitionReceiver(Context context) {
        //TODO: nbrown 1/16/2020 - check that user has agreed to passive data collection before adding ActivityTransitionReceiver

        //TODO: nbrown 1/16/2020 - add or remove receiver when user updates their passive data collection setting
        // -See iOS for where this setting is stored as well as UI for updating
        // -iOS prompts the user after the walk and balance task if it has never been set
        // -In bridge study manager will need to update Configuration Elements -> SettingsDataSource -> passiveDataAllowed to no longer be hidden on Android

        Context applicationContext = context.getApplicationContext();
        List<ActivityTransition> transitions = new ArrayList<>();

        transitions.add(
                new ActivityTransition.Builder()
                        .setActivityType(DetectedActivity.WALKING)
                        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                        .build());

        transitions.add(
                new ActivityTransition.Builder()
                        .setActivityType(DetectedActivity.WALKING)
                        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                        .build());

        ActivityTransitionRequest request = new ActivityTransitionRequest(transitions);

        Intent intent = new Intent(applicationContext, ActivityTransitionReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(applicationContext, 1, intent, 0);

        Task<Void> task = ActivityRecognition.getClient(applicationContext)
                .requestActivityTransitionUpdates(request, pendingIntent);

        task.addOnSuccessListener(
                new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        // Handle success
                        Log.d("MPower", "Activity Transition Registration Success");
                    }
                }
        );

        task.addOnFailureListener(
                new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        // Handle error
                        Log.d("MPower", "Activity Transition Registration Failure");
                    }
                }
        );
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // In some cases modifying newConfig leads to unexpected behavior,
        // so it's better to edit new instance.
        Configuration configuration = new Configuration(newConfig);
        if (configuration.fontScale > 1.3) {
            configuration.fontScale = 1.3f;
            Context context = getApplicationContext();
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();

            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) {
                wm.getDefaultDisplay().getMetrics(metrics);
            }

            metrics.scaledDensity = configuration.fontScale * metrics.density;
            context.getResources().updateConfiguration(configuration, metrics);
        }
    }
}
