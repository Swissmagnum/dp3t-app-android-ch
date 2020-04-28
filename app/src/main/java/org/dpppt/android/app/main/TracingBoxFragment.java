package org.dpppt.android.app.main;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import org.dpppt.android.app.R;
import org.dpppt.android.app.main.model.TracingState;
import org.dpppt.android.app.util.DeviceFeatureHelper;
import org.dpppt.android.app.util.TracingErrorStateHelper;
import org.dpppt.android.app.util.TracingStatusHelper;
import org.dpppt.android.app.viewmodel.TracingViewModel;
import org.dpppt.android.sdk.TracingStatus;

import static org.dpppt.android.app.onboarding.OnboardingLocationPermissionFragment.REQUEST_CODE_ASK_PERMISSION_FINE_LOCATION;

public class TracingBoxFragment extends Fragment {


	private static final int REQUEST_CODE_BLE_INTENT = 330;
	private static final int REUQEST_CODE_BATTERY_OPTIMIZATIONS_INTENT = 420;
	private static final int REQUEST_CODE_LOCATION_INTENT = 510;
	private TracingViewModel tracingViewModel;


	private View tracingStatusView;

	private View tracingErrorView;

	public TracingBoxFragment() {
		super(R.layout.fragment_tracing_box);
	}

	public static TracingBoxFragment newInstance() {
		Bundle args = new Bundle();
		TracingBoxFragment fragment = new TracingBoxFragment();
		fragment.setArguments(args);
		return fragment;
	}


	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		tracingViewModel = new ViewModelProvider(requireActivity()).get(TracingViewModel.class);
	}



	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		tracingStatusView = view.findViewById(R.id.tracing_status);
		tracingErrorView = view.findViewById(R.id.tracing_error);

		showStatus();
	}

	private void showStatus() {
		tracingViewModel.getAppStatusLiveData().observe(getViewLifecycleOwner(), tracingStatusInterface -> {
			boolean isTracing = tracingStatusInterface.getTracingState().equals(TracingState.ACTIVE);

			TracingStatus.ErrorState errorState = tracingStatusInterface.getTracingErrorState();
			if (errorState != null) {
				handleErrorState(errorState);
			} else if (!isTracing) {
				tracingStatusView.setVisibility(View.GONE);
				tracingErrorView.setVisibility(View.VISIBLE);
				TracingStatusHelper.showTracingDeactivated(tracingErrorView);
			} else if (tracingStatusInterface.isReportedAsInfected()) {
				tracingStatusView.setVisibility(View.VISIBLE);
				tracingErrorView.setVisibility(View.GONE);
				TracingStatusHelper.updateStatusView(tracingStatusView, TracingState.ENDED);
			} else {
				tracingStatusView.setVisibility(View.VISIBLE);
				tracingErrorView.setVisibility(View.GONE);
				TracingStatusHelper.updateStatusView(tracingStatusView, TracingState.ACTIVE);
			}
		});
	}

	private void handleErrorState(TracingStatus.ErrorState errorState) {
		tracingStatusView.setVisibility(View.GONE);
		tracingErrorView.setVisibility(View.VISIBLE);
		TracingErrorStateHelper.updateErrorView(tracingErrorView, errorState);
		tracingErrorView.findViewById(R.id.error_status_button).setOnClickListener(v -> {
			switch (errorState) {
				case MISSING_LOCATION_PERMISSION:
					if (ActivityCompat
							.shouldShowRequestPermissionRationale(requireActivity(),
									Manifest.permission.ACCESS_FINE_LOCATION)) {
						String[] permissions = new String[] { Manifest.permission.ACCESS_FINE_LOCATION };
						requestPermissions(permissions, REQUEST_CODE_ASK_PERMISSION_FINE_LOCATION);
					} else {
						new AlertDialog.Builder(requireActivity())
								.setTitle(R.string.button_permission_location_android)
								.setMessage(R.string.foreground_service_notification_error_location_permission)
								.setPositiveButton(getString(R.string.button_ok),
										(dialogInterface, i) -> {
											DeviceFeatureHelper.openApplicationSettings(requireActivity());
											dialogInterface.dismiss();
										})
								.create()
								.show();
					}
					break;
				case BLE_DISABLED:
					BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
					if (!mBluetoothAdapter.isEnabled()) {
						Intent bleIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
						startActivityForResult(bleIntent, REQUEST_CODE_BLE_INTENT);
					}
					break;
				case LOCATION_SERVICE_DISABLED:
					Intent locationInent = new Intent(
							Settings.ACTION_LOCATION_SOURCE_SETTINGS);
					startActivityForResult(locationInent, REQUEST_CODE_LOCATION_INTENT);
					break;
				case BATTERY_OPTIMIZER_ENABLED:
					String packageName = requireActivity().getPackageName();
					Intent batteryIntent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
					batteryIntent.setData(Uri.parse("package:" + packageName));
					startActivityForResult(batteryIntent, REUQEST_CODE_BATTERY_OPTIMIZATIONS_INTENT);
					break;
			}
		});
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
		if (requestCode == REQUEST_CODE_BLE_INTENT && resultCode == Activity.RESULT_OK) {
			tracingViewModel.invalidateService();
		} else if (requestCode == REUQEST_CODE_BATTERY_OPTIMIZATIONS_INTENT && resultCode == Activity.RESULT_OK) {
			tracingViewModel.invalidateService();
		} else if (requestCode == REQUEST_CODE_LOCATION_INTENT && resultCode == Activity.RESULT_OK) {
			tracingViewModel.invalidateService();
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		if (requestCode == REQUEST_CODE_ASK_PERMISSION_FINE_LOCATION) {
			if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				tracingViewModel.invalidateService();
			}
		}
	}

}