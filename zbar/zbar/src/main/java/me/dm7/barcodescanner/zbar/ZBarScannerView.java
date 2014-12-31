package me.dm7.barcodescanner.zbar;

import android.content.Context;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.text.TextUtils;
import android.util.AttributeSet;

import net.sourceforge.zbar.Config;
import net.sourceforge.zbar.Image;
import net.sourceforge.zbar.ImageScanner;
import net.sourceforge.zbar.Symbol;
import net.sourceforge.zbar.SymbolSet;

import java.util.Collection;
import java.util.List;

import me.dm7.barcodescanner.core.BarcodeScannerView;
import me.dm7.barcodescanner.core.CameraPreviewDescriptor;
import me.dm7.barcodescanner.core.DisplayUtils;

public class ZBarScannerView extends BarcodeScannerView {

    public interface ResultHandler {
        public void handleResult(Result rawResult);
    }

    /**
     * Interface for communicating with the caller. This
     * interface will pass the preview data back tot he caller
     * for each frame.
     */
    public interface PreviewCallback {
        public void onPreviewFrame(CameraPreviewDescriptor descriptor);
    }

    static {
        System.loadLibrary("iconv");
    }

    private ImageScanner mScanner;
    private List<BarcodeFormat> mFormats;
    private ResultHandler mResultHandler;
    private PreviewCallback previewCallback;

    public ZBarScannerView(Context context) {
        super(context);
        setupScanner();
    }

    public ZBarScannerView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        setupScanner();
    }

    public void setPreviewCallback(PreviewCallback previewCallback) {
        this.previewCallback = previewCallback;
    }

    public void setFormats(List<BarcodeFormat> formats) {
        mFormats = formats;
        setupScanner();
    }

    public void setResultHandler(ResultHandler resultHandler) {
        mResultHandler = resultHandler;
    }

    public Collection<BarcodeFormat> getFormats() {
        if(mFormats == null) {
            return BarcodeFormat.ALL_FORMATS;
        }
        return mFormats;
    }

    public void setupScanner() {
        mScanner = new ImageScanner();
        mScanner.setConfig(0, Config.X_DENSITY, 3);
        mScanner.setConfig(0, Config.Y_DENSITY, 3);

        mScanner.setConfig(Symbol.NONE, Config.ENABLE, 0);
        for(BarcodeFormat format : getFormats()) {
            mScanner.setConfig(format.getId(), Config.ENABLE, 1);
        }
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {

        if(previewCallback != null) {
            Camera.Parameters params = camera.getParameters();
            Camera.Size s = params.getPreviewSize();
            int previewFormat = params.getPreviewFormat();
            CameraPreviewDescriptor descriptor = new CameraPreviewDescriptor(data, previewFormat, s.width, s.height);
            previewCallback.onPreviewFrame(descriptor);
        }

        Camera.Parameters parameters = camera.getParameters();
        Camera.Size size = parameters.getPreviewSize();
        int width = size.width;
        int height = size.height;

        if(DisplayUtils.getScreenOrientation(getContext()) == Configuration.ORIENTATION_PORTRAIT) {
            byte[] rotatedData = new byte[data.length];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++)
                    rotatedData[x * height + height - y - 1] = data[x + y * width];
            }
            int tmp = width;
            width = height;
            height = tmp;
            data = rotatedData;
        }

        Image barcode = new Image(width, height, "Y800");
        barcode.setData(data);

        int result = mScanner.scanImage(barcode);

        if (result != 0) {
            stopCamera();
            if(mResultHandler != null) {
                SymbolSet syms = mScanner.getResults();
                Result rawResult = new Result();
                for (Symbol sym : syms) {
                    String symData = sym.getData();
                    if (!TextUtils.isEmpty(symData)) {
                        rawResult.setContents(symData);
                        rawResult.setBarcodeFormat(BarcodeFormat.getFormatById(sym.getType()));
                        break;
                    }
                }
                mResultHandler.handleResult(rawResult);
            }
        } else {
            camera.setOneShotPreviewCallback(this);
        }
    }
}
