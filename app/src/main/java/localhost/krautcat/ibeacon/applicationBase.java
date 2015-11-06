package localhost.krautcat.ibeacon;

import android.app.Application;
import com.estimote.sdk.EstimoteSDK;

/**
 * Created by krautcat on 05.11.15.
 */
public class applicationBase extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Initializes Estimote SDK with your App ID and App Token from Estimote Cloud.
        // You can find your App ID and App Token in the
        // Apps section of the Estimote Cloud (http://cloud.estimote.com).
        EstimoteSDK.initialize(this, "ibeacon-krautcat-universit-hyw", "7570e3a02c04d9087a22c7ee11a09128");

        // Configure verbose debug logging.
        EstimoteSDK.enableDebugLogging(true);
    }
}
