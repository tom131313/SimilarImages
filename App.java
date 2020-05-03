package app;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.PrintStream;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.highgui.HighGui;
import org.opencv.imgcodecs.Imgcodecs;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.h2.tools.DeleteDbFiles;

public class App {
	static {System.loadLibrary(Core.NATIVE_LIBRARY_NAME);} // Load the native OpenCV library

	static App x = new App();
	static Connection conn;
	static Statement statementInsert; // filling the database with jpg filenames
	static PreparedStatement statementInner; // inner loop of subsequent filenames
	static Statement statementOuter; // outer loop of all filenames

	static int id = 0;
	static int maxDifferences;
	static boolean BW = true;//true; // need channel 0
	static boolean COLOR = true; // need channels 1 and 2

    public static void main(String[] args) throws Exception {

		if ( BW && COLOR ) {
			maxDifferences = 12;
		}
		else {
			maxDifferences = 9;
		}
		
		DeleteDbFiles.execute("./", "signature", true); // DELETE DB

        Class.forName("org.h2.Driver");
        conn = DriverManager.getConnection("jdbc:h2:./signature"); // also url, userid, password
		statementInsert = conn.createStatement();
		statementOuter = conn.createStatement();
		
		statementOuter.execute("create table Signature(id int primary key,"
			 + "signature1 long,signature2 long,signature3 long,signature4 long,"
			 + "filename varchar)"); // CREATE DB

		// create file for the System.out
		FileOutputStream fout=new FileOutputStream("signature.txt");   
		PrintStream out=new PrintStream(fout); 
		System.setOut(out);
		//! create file for the System.out

		Filewalker fw = x.new Filewalker(); //  SEARCH FOR JPG
		fw.walk("E:\\Vicki pictures from laptop 4-20\\Pictures\\" ); // SEARCH FOR JPG
		fw.walk("e:\\VickiPhotosFromBigComputer\\" ); // SEARCH FOR JPG
//		fw.walk("C:\\Users\\RKT\\frc\\FRC2020\\Code\\Similar\\" ); // SEARCH FOR JPG
		
		statementInsert.close();

		findSimilarImages();

		statementInner.close();
		statementOuter.close();
		conn.close();
		System.err.println();
		System.exit(0);
	}

	static void findSimilarImages() throws Exception {
		ResultSet rsOuter = statementOuter.executeQuery("select * from signature");
		statementInner = conn.prepareCall("SELECT * FROM signature WHERE id > ?");
		boolean displayImages = true;

		while (rsOuter.next()) { // outer loop over all lines
			statementInner.setInt(1, rsOuter.getInt("id")); // set the start of the next loop as the current position in this loop - diagonal half of a matrix
			ResultSet rsInner = statementInner.executeQuery();
	
			while (rsInner.next()) { // inner loop over the rest - diagonal half to find similarities
				// compute similarity index
				// to get number of 1's in the difference - lower count means more similar
				int similarity;
				similarity =
					  Long.bitCount(rsOuter.getLong("signature1") ^ rsInner.getLong("signature1"))
					+ ((Long.bitCount(rsOuter.getLong("signature2") ^ rsInner.getLong("signature2"))
					+ Long.bitCount(rsOuter.getLong("signature3") ^ rsInner.getLong("signature3"))) / 4)
					+ Long.bitCount(rsOuter.getLong("signature4") ^ rsInner.getLong("signature4"));

				if ( similarity <= maxDifferences)
				{
					// Similar Images
					if( displayImages )
						{
						Mat src = Imgcodecs.imread(rsOuter.getString("filename"));
						HighGui.imshow("A " + similarity + " " + rsOuter.getString("filename"), src);
						int rc = HighGui.waitKey(0);
						if ( rc == 27) displayImages = false; // esc stops display of images
						else
						{
						src = Imgcodecs.imread(rsInner.getString("filename"));
						HighGui.imshow("B " + similarity + " "  + rsInner.getString("filename"), src);
						rc = HighGui.waitKey(0);
						if ( rc == 27) displayImages = false;
						}
						}
						//System.out.println(similarity + ", " + rsOuter.getString("filename") + " || " + rsInner.getString("filename") );
						System.out.format("%02d, %s || %s\n", similarity, rsOuter.getString("filename"), rsInner.getString("filename") );
					}
				//System.out.println(rs.getInt("id") + ", " + rs.getLong("signature") + ", " + rs.getString("filename"));
			}
		rsInner.close();
		}
		rsOuter.close();
	}

	public class Filewalker {

		public void walk( String path ) {

			File root = new File( path );

			// // Create a FilenameFilter 
            // FilenameFilter filter = new FilenameFilter() { 
  
            //     public boolean accept(File f, String name) 
            //     { 
			// 		System.out.println(f + " <> " + name);
					
            //         return false;//true;//name.endsWith(".jpg"); 
            //     } 
			// }; 
			
			// Create a FilenameFilter 
            FileFilter filter = new FileFilter() { 
  
                public boolean accept(File file) 
                { 
					//System.out.print(f.isDirectory()?"DIR: ":f.isFile()?"FILE: ":"NONE");
					//System.out.println(f + " <> " + f.getAbsoluteFile() + "{}" + f.getAbsolutePath());
					
					return file.isDirectory()// process all directories and files ending with ".jpg"
					|| (file.canRead()
						&& file.getName().length()>=3
						&& file.getName().substring(file.getName().length()-3).equalsIgnoreCase("JPG"));
//					|| (file.canRead()) && (file.getName().endsWith(".jpg") || file.getName().endsWith(".JPG"));
                } 
			}; 
			//! Create a FilenameFilter

			File[] list = root.listFiles(filter);
	
			if (list == null) return;
	
			for ( File file : list ) {
				if ( file.isDirectory() ) {
					walk( file.getAbsolutePath() ); // recursive invocation to subdirectory
					//System.out.println( "Dir:" + f.getAbsoluteFile() );
				}
				else 
				{
					// process this file - get its signature
					System.err.format("\r%s            ", file.getAbsoluteFile().toString());
					new CompressImage().run(file.getAbsoluteFile().toString());
				}
			}
		}
	}
	
	class CompressImage {

		public void run(String arg) {

        // [Load image]
        String filename = arg.length() > 0 ? arg : "data/lenabig.jpg";
		Mat src=null;
		GripPipelineSimilar signatureImage=null;

		try {
		src = Imgcodecs.imread(filename);
    	if (src.empty()) {
            System.err.format("\n\n\nCannot read image: %s      \n\n\n", filename);
            return;
		}
        //! [Load image]
 
		// [Compute Hash]
		signatureImage = new GripPipelineSimilar();

		long[] hash = {0, 0, 0, 0};
		
		for(int channel = (BW ? 0 : COLOR ? 1 : 99) ; channel <= (COLOR ? 2 : BW ? 0 : -99); channel++) {
			signatureImage.process(src, channel);
		
			// System.out.println(similarity.cvAdaptivethresholdOutput());
			int size = (int)signatureImage.cvAdaptivethresholdOutput().total();
			//System.out.println("size " + size);

			byte[] temp = new byte[size];

			signatureImage.cvAdaptivethresholdOutput().get(0, 0, temp); //

			// compress the 64 bits from each channel into 3 longs - hash[0] to hash[3] 3 is still 0
			long mask = 1;

			for (int idx = 0; idx < Math.min(64, temp.length); idx++) {
				if( temp[idx] != 0) {
					hash[channel] = hash[channel] | mask;
				}
				mask = mask << 1;
			}
		} // end loop processing the 3 image channels Y U V

		id++; // count file number used as primary key in DB

		statementInsert.execute("insert into Signature values(" + id + ", "
			+ hash[0] + "," + hash[1] + ","+ hash[2] + ","+ hash[3] + ","
			+ "'" + filename.replace("'", "''") + "')"
			);

		//! [Compute Hash]

		} catch(Exception e) {
			System.err.format("\n\n\nCannot process image:  %s  %s   \n\n\n",  filename, e.toString());
		 	return;
	}
		finally {
			src.release();
			signatureImage.releaseMats(); // done with the Mats
		}
	//System.err.print(Runtime.getRuntime().freeMemory() + "  ");
    }
	}
}
/*

Calculating the sum of the squares of the differences of the pixel colour values of a drastically scaled-down version (eg: 6x6 pixels) works nicely. Identical images yield 0, similar images yield small numbers, different images yield big ones.

The other guys above's idea to break into YUV first sounds intriguing - while my idea works great, I want my images to be calculated as "different" so that it yields a correct result - even from the perspective of a colourblind observer.

*Do not compare RGB (red,green,blue). Compare Brightness as half the weight and compare color/hue as the other half (or 2/3rds vs 1/3rd). Calculate the difference in values and depending on 'tolerance' value they are the same or they are not.



findimagedupes compares a list of files for visual similarity.

       To calculate an image fingerprint:
         1) Read image.
         2) Resample to 160x160 to standardize size.
         3) Grayscale by reducing saturation.
         4) Blur a lot to get rid of noise.
         5) Normalize to spread out intensity as much as possible.
         6) Equalize to make image as contrasty as possible.
         7) Resample again down to 16x16.
         8) Reduce to 1bpp.
         9) The fingerprint is this raw image data.

       To compare two images for similarity:
         1) Take fingerprint pairs and xor them.
         2) Compute the percentage of 1 bits in the result.
         3) If percentage exceeds threshold, declare files to be similar.

*/
/*
package com.isaac.models;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.isaac.utils.Filters;
import com.isaac.utils.FeatureWeight;
import com.isaac.utils.ImgDecompose;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;

public class FusionEnhance {

	public static Mat enhance (Mat image, int level) {
		// color balance
		Mat img1 = Filters.SimplestColorBalance(image, 5);
		img1.convertTo(img1, CvType.CV_8UC1);
		// Perform sRGB to CIE Lab color space conversion
		Mat LabIm1 = new Mat();
		Imgproc.cvtColor(img1, LabIm1, Imgproc.COLOR_BGR2Lab);
		Mat L1 = new Mat();
		Core.extractChannel(LabIm1, L1, 0);
		// apply CLAHE
		Mat[] result = applyCLAHE(LabIm1, L1);
		Mat img2 = result[0];
		Mat L2 = result[1];
		// calculate normalized weight
		Mat w1 = calWeight(img1, L1);
		Mat w2 = calWeight(img2, L2);
		Mat sumW = new Mat();
		Core.add(w1, w2, sumW);
		Core.divide(w1, sumW, w1);
		Core.divide(w2, sumW, w2);
		// merge image1 and image2
		return ImgDecompose.fuseTwoImage(w1, img1, w2, img2, level);
	}

	private static Mat[] applyCLAHE(Mat img, Mat L) {
		Mat[] result = new Mat[2];
		CLAHE clahe = Imgproc.createCLAHE();
		clahe.setClipLimit(2.0);
		Mat L2 = new Mat();
		clahe.apply(L, L2);
		Mat LabIm2 = new Mat();
		List<Mat> lab = new ArrayList<>();
		Core.split(img, lab);
		Core.merge(new ArrayList<>(Arrays.asList(L2, lab.get(1), lab.get(2))), LabIm2);
		Mat img2 = new Mat();
		Imgproc.cvtColor(LabIm2, img2, Imgproc.COLOR_Lab2BGR);
		result[0] = img2;
		result[1] = L2;
		return result;
	}

	private static Mat calWeight(Mat img, Mat L) {
		Core.divide(L, new Scalar(255.0), L);
		L.convertTo(L, CvType.CV_32F);
		// calculate laplacian contrast weight
		Mat WL = FeatureWeight.LaplacianContrast(L);
		WL.convertTo(WL, L.type());
		// calculate Local contrast weight
		Mat WC = FeatureWeight.LocalContrast(L);
		WC.convertTo(WC, L.type());
		// calculate the saliency weight
		Mat WS = FeatureWeight.Saliency(img);
		WS.convertTo(WS, L.type());
		// calculate the exposedness weight
		Mat WE = FeatureWeight.Exposedness(L);
		WE.convertTo(WE, L.type());
		// sum
		Mat weight = WL.clone();
		Core.add(weight, WC, weight);
		Core.add(weight, WS, weight);
		Core.add(weight, WE, weight);
		return weight;
	}
*/