package app;

// Similarity between 2 images

// Mean Structural Similarity (MSSIM)
// The MSSIM method converted to Java from the C++ from:
// https://docs.opencv.org/master/d5/dc4/tutorial_video_input_psnr_ssim.html
// Video Input with OpenCV and similarity measurement

// usage:     double mssim = MSSIM.getMSSIM( src1, src2 ).val[0];
// mssim == 1.0 is src1 and src2 essentially identical;  mssim > 0.9 similar; mssim < 0.4 very different;  mssim == 0.0 completely different
// good index seems to have fewer false similars than the signature/hash method but at a huge cost

import org.opencv.imgproc.Imgproc;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.core.Scalar;
import org.opencv.core.Core;

public class MSSIM
{
    static Scalar getMSSIM( Mat iA, Mat iB) {

    Mat i1 = Mat.zeros(1, 1, CvType.CV_8UC1);
    Mat i2 = Mat.zeros(1, 1, CvType.CV_8UC1);
    Imgproc.resize(iA, i1, new Size(128, 128), 0., 0., Imgproc.INTER_LINEAR);
    Imgproc.resize(iB, i2, new Size(128, 128), 0., 0., Imgproc.INTER_LINEAR);
    final Scalar C1 = new Scalar(6.5025), C2 = new Scalar(58.5225);

    /***************************** INITS **********************************/
    desaturate(i1, i1);
    desaturate(i2, i2);

    int d     = CvType.CV_32F;

    Mat I1 = new Mat();
    Mat I2 = new Mat();
    i1.convertTo(I1, d);           // cannot calculate on one byte large values
    i2.convertTo(I2, d);
    i1.release();
    i2.release();

    Mat I2_2   = I2.mul(I2);        // I2^2
    Mat I1_2   = I1.mul(I1);        // I1^2
    Mat I1_I2  = I1.mul(I2);        // I1 * I2

    /*************************** END INITS **********************************/

    Mat mu1 = new Mat();
    Mat mu2 = new Mat();   // PRELIMINARY COMPUTING
    Imgproc.blur(I1, mu1, new Size(9, 9));
    Imgproc.blur(I2, mu2, new Size(9, 9));

    I1.release();
    I2.release();

    //Imgproc.GaussianBlur(I1, mu1, new Size(11, 11), 1.5);
    //Imgproc.GaussianBlur(I2, mu2, new Size(11, 11), 1.5);

    Mat mu1_2   =   mu1.mul(mu1);
    Mat mu2_2   =   mu2.mul(mu2);
    Mat mu1_mu2 =   mu1.mul(mu2);

    mu1.release();
    mu2.release();

    Mat sigma1_2=new Mat(), sigma2_2=new Mat(), sigma12=new Mat();

    Imgproc.blur(I1_2, sigma1_2, new Size(9, 9)); // box filter - good and fast
    //Imgproc.GaussianBlur(I1_2, sigma1_2, new Size(11, 11), 1.5);
    //sigma1_2 -= mu1_2;
    Core.subtract(sigma1_2, mu1_2, sigma1_2);

    Imgproc.blur(I2_2, sigma2_2, new Size(9, 9)); // box filter - good and fast
    //Imgproc.GaussianBlur(I2_2, sigma2_2, new Size(11, 11), 1.5);
    //sigma2_2 -= mu2_2;
    Core.subtract(sigma2_2, mu2_2, sigma2_2);

    Imgproc.blur(I1_I2, sigma12, new Size(9, 9)); // box filter - good and fast
    I2_2.release();
    I1_2.release();
    I1_I2.release();
    //Imgproc.GaussianBlur(I1_I2, sigma12, new Size(11, 11), 1.5);
    //sigma12 -= mu1_mu2;
    Core.subtract(sigma12, mu1_mu2, sigma12);

    ///////////////////////////////// FORMULA ////////////////////////////////
    Mat t1= new Mat(), t2= new Mat(), t3= new Mat();

    //t1 = 2 * mu1_mu2 + C1;
 
    Core.multiply(mu1_mu2, new Scalar(2., 2., 2.), t1);
    Core.add(t1, C1, t1);
 
    //t2 = 2 * sigma12 + C2;
    Core.multiply(sigma12, new Scalar(2. , 2., 2.), t2);
    Core.add(t2, C2, t2);

    t3 = t1.mul(t2);              // t3 = ((2*mu1_mu2 + C1).*(2*sigma12 + C2))

    //t1 = mu1_2 + mu2_2 + C1;
    Core.add(mu1_2, mu2_2, t1);
    Core.add(t1, C1, t1);
   
    //t2 = sigma1_2 + sigma2_2 + C2;
    Core.add(sigma1_2, sigma2_2, t2);
    Core.add(t2, C2, t2);
 
    t1 = t1.mul(t2);               // t1 =((mu1_2 + mu2_2 + C1).*(sigma1_2 + sigma2_2 + C2))
 
    Mat ssim_map=new Mat();
    Core.divide(t3, t1, ssim_map);      // ssim_map =  t3./t1;

    Scalar mssim = Core.mean( ssim_map ); // mssim = average of ssim map

    t1.release();
    t2.release();
    t3.release();
    mu1_2.release();
    mu2_2.release();
    mu1_mu2.release();
    sigma1_2.release();
    sigma2_2.release();
    sigma12.release();
    ssim_map.release();

    return mssim;
}
/**
	 * Converts a color image into shades of grey.
	 * @param input The image on which to perform the desaturate.
	 * @param output The image in which to store the output.
	 */
	static private void desaturate(Mat input, Mat output) {
		switch (input.channels()) {
			case 1:
				// If the input is already one channel, it's already desaturated
				input.copyTo(output);
				break;
			case 3:
				Imgproc.cvtColor(input, output, Imgproc.COLOR_BGR2GRAY);
				break;
			case 4:
				Imgproc.cvtColor(input, output, Imgproc.COLOR_BGRA2GRAY);
				break;
			default:
				throw new IllegalArgumentException("Input to desaturate must have 1, 3, or 4 channels");
		}
	}
}
/////////////////
// Scalar getMSSIM( const Mat& i1, const Mat& i2)
// {
//     const double C1 = 6.5025, C2 = 58.5225;
//     /***************************** INITS **********************************/
//     int d     = CV_32F;

//     Mat I1, I2;
//     i1.convertTo(I1, d);           // cannot calculate on one byte large values
//     i2.convertTo(I2, d);

//     Mat I2_2   = I2.mul(I2);        // I2^2
//     Mat I1_2   = I1.mul(I1);        // I1^2
//     Mat I1_I2  = I1.mul(I2);        // I1 * I2

//     /*************************** END INITS **********************************/

//     Mat mu1, mu2;   // PRELIMINARY COMPUTING
//     GaussianBlur(I1, mu1, Size(11, 11), 1.5);
//     GaussianBlur(I2, mu2, Size(11, 11), 1.5);

//     Mat mu1_2   =   mu1.mul(mu1);
//     Mat mu2_2   =   mu2.mul(mu2);
//     Mat mu1_mu2 =   mu1.mul(mu2);

//     Mat sigma1_2, sigma2_2, sigma12;

//     GaussianBlur(I1_2, sigma1_2, Size(11, 11), 1.5);
//     sigma1_2 -= mu1_2;

//     GaussianBlur(I2_2, sigma2_2, Size(11, 11), 1.5);
//     sigma2_2 -= mu2_2;

//     GaussianBlur(I1_I2, sigma12, Size(11, 11), 1.5);
//     sigma12 -= mu1_mu2;

//     ///////////////////////////////// FORMULA ////////////////////////////////
//     Mat t1, t2, t3;

//     t1 = 2 * mu1_mu2 + C1;
//     t2 = 2 * sigma12 + C2;
//     t3 = t1.mul(t2);              // t3 = ((2*mu1_mu2 + C1).*(2*sigma12 + C2))

//     t1 = mu1_2 + mu2_2 + C1;
//     t2 = sigma1_2 + sigma2_2 + C2;
//     t1 = t1.mul(t2);               // t1 =((mu1_2 + mu2_2 + C1).*(sigma1_2 + sigma2_2 + C2))

//     Mat ssim_map;
//     divide(t3, t1, ssim_map);      // ssim_map =  t3./t1;

//     Scalar mssim = mean( ssim_map ); // mssim = average of ssim map
//     return mssim;
// }
