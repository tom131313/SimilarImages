package app;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.highgui.HighGui;
import org.opencv.imgcodecs.Imgcodecs;

import org.h2.tools.DeleteDbFiles;

public class App {
  static {
    System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
  } // Load the native OpenCV library

  // [Kohonen vector output for post processing]
  static final boolean kohRun = true; // user specified switch to put out signature vectors or not for Kohonen
  // following definitions only for Kohonen
  static FileOutputStream koh; // similarity vector for Kohonen process
  static PrintStream kohVectors;
  static boolean firstTimeWriteKohNumDimensions;
  // ! [Kohonen vector output for post processing]

  static final App x = new App();
  static Connection conn;
  static Statement statementInsert; // filling the database with jpg filenames
  static PreparedStatement statementInner; // inner loop of subsequent filenames
  static Statement statementOuter; // outer loop of all filenames

  static int id = 0;

  //////////////////////////////////////////////////
  // U S E R S E T T A B L E P A R A M E T E R S

  static final boolean BW = true; // compares channel 0 - Y of YUV
  static final boolean COLOR = true; // compares channels 1 and 2 - UV of YUV
  static boolean displayImages = true; // display similar images
  // max number of "dimensions" different to consider similar
  // suggest if BW && COLOR maxDifferences = 12 else maxDifferences = 9
  static final int maxDifferences = 12;

  static final boolean doMSSIM = true;
  // additional refinement for differences uses computer hog MSSIM
  // only used for possible matches from the signature comparison (maxDifferences)
  // which occasionally matches 2 very different images
  // 1. to .8 is very similar
  // .8 to .4 is similar
  // .4 to .2 is not much similarity maybe the same scene
  // below .2 is different
  static final double maxDifferencesMSSIM = .25;

  //////////////////////////////////////////////////
  //////////////////////////////////////////////////

  // create file for the messages
  static FileOutputStream fout;
  static PrintStream similarFiles;

  // ! create file for the messages

  // Create a stream to hold the OpenCV imread err output
  static ByteArrayOutputStream baos = new ByteArrayOutputStream();
  static PrintStream ps = new PrintStream(baos);
  static PrintStream realErr = System.err; // the normal err output when not doing imread

  public static void main(String[] args) throws Exception {
    fout = new FileOutputStream("similarImages.txt");
    similarFiles = new PrintStream(fout);

    if (kohRun) {
      firstTimeWriteKohNumDimensions = true;
      koh = new FileOutputStream("kohVectors.txt");
      kohVectors = new PrintStream(koh);
    }

    System.setErr(realErr);

    DeleteDbFiles.execute("./", "signature", true); // DELETE DB - fresh start for this run

    Class.forName("org.h2.Driver");
    conn = DriverManager.getConnection("jdbc:h2:./signature"); // also url, userid, password

    statementInsert = conn.createStatement();
    statementOuter = conn.createStatement();

    statementOuter.execute("create table Signature(id int primary key,"
        + "signature1 long,signature2 long,signature3 long,signature4 long," + "filename varchar)"); // CREATE DB

    Filewalker fw = x.new Filewalker(); // SEARCH FOR JPG
    // fw.walk("data"); // SEARCH FOR JPG
    fw.walk("F:\\Pictures\\"); // SEARCH FOR JPG
    // fw.walk("C:\\VDT\\" ); // SEARCH FOR JPG
    // fw.walk("e:\\VickiPhotosFromBigComputer\\" ); // SEARCH FOR JPG

    statementInsert.close();

    System.out.println();
    // System.out.println("Free memory after reading all files " +
    // Runtime.getRuntime().freeMemory());

    x.findSimilarImages();

    statementInner.close();
    statementOuter.close();
    conn.close();
    System.exit(0);
  }

  void findSimilarImages() throws Exception {

    System.out.println(
        "\nQ or q at any time stops display of subsequent similar images;\nlog file of all similar images is completed, however.\n\n");
    ResultSet rsOuter = statementOuter.executeQuery("select * from signature");
    statementInner = conn.prepareCall("SELECT * FROM signature WHERE id > ?");

    double mssim;

    while (rsOuter.next()) { // outer loop over all lines'
      // System.err.println(rsOuter.getInt("id") + " " +
      // Runtime.getRuntime().freeMemory());
      statementInner.setInt(1, rsOuter.getInt("id")); // set the start of the next loop as the current position in this
                                                      // loop - diagonal half of a matrix
      ResultSet rsInner = statementInner.executeQuery();

      while (rsInner.next()) { // inner loop over the rest - diagonal half to find similarities
        // Compute similarity index as the number differences in the bits of the
        // compressed
        // signature. That is the number of 1's in the XOR difference
        // Lower count means more similar
        int similarity;
        similarity = Long.bitCount(rsOuter.getLong("signature1") ^ rsInner.getLong("signature1"))
            + ((Long.bitCount(rsOuter.getLong("signature2") ^ rsInner.getLong("signature2"))
                + Long.bitCount(rsOuter.getLong("signature3") ^ rsInner.getLong("signature3"))) / 4)
            + Long.bitCount(rsOuter.getLong("signature4") ^ rsInner.getLong("signature4"));

        if (similarity <= maxDifferences) {

          mssim = 0;
          if (doMSSIM) {
            // mssim technique seems to have fewer false similars than the signature/hash
            // but at huge cost for reading entire images n^2/2 times instead of the
            // short signature of images which usually can be stored in memory.
            // too much reading of images from a mechanical disk drive will destroy
            // the drive (I know).

            // Get the 2 images (again)
            // Run the MSSIM
            // Print the MSSIM if near 1
            // This isn't efficient - shouldn't have to get the first image more than once
            // for the mu and std but that isn't
            // much compared to the covariance calcs

            // need to have 2 images at once
            // tried to declare Mat srcBmssim outside the inner loop but imread has a huge
            // memory leak so allocate and release each time
            Mat srcAmssim = Imgcodecs.imread(rsOuter.getString("filename"), Imgcodecs.IMREAD_UNCHANGED);
            Mat srcBmssim = Imgcodecs.imread(rsInner.getString("filename"), Imgcodecs.IMREAD_UNCHANGED);
            mssim = MSSIM.getMSSIM(srcAmssim, srcBmssim).val[0]; // roughly > 0.4 similar; < 0.4 disimilar;
            srcAmssim.release();
            srcBmssim.release();
          }

          if (!doMSSIM || mssim >= maxDifferencesMSSIM) { // use the initial mssim for printing below if not calculating it
            // Similar Images
            similarFiles.format("%02d, %4.2f, %d:%d, %s || %s\n", similarity, mssim, rsOuter.getInt("id"),
                rsInner.getInt("id"), rsOuter.getString("filename"), rsInner.getString("filename"));

            if (displayImages) {
              File fileA = new File(rsOuter.getString("filename"));
              File fileB = new File(rsInner.getString("filename"));
              if (fileA.exists() && fileB.exists()) { // might have been deleted already
                Mat srcA = Imgcodecs.imread(rsOuter.getString("filename"));
                Mat srcB = Imgcodecs.imread(rsInner.getString("filename"));
                HighGui.imshow("A " + similarity + " " + String.format("%4.2f ", mssim) + rsOuter.getString("filename"),
                    srcA);
                // HighGui.imshow("A " + similarity + " " + rsOuter.getString("filename"),
                // srcA);
                int rc = HighGui.waitKey(0);
                if (rc == 81 || rc == 113)
                  displayImages = false; // Q or q stops display of images (27 == esc)
                else {
                  HighGui.imshow(
                      "B " + similarity + " " + String.format("%4.2f ", mssim) + rsInner.getString("filename"), srcB);
                  // HighGui.imshow("B " + similarity + " " + rsInner.getString("filename"),
                  // srcB);
                  rc = HighGui.waitKey(0);
                  if (rc == 81 || rc == 113)
                    displayImages = false;
                }
                srcA.release();
                srcB.release();
                displayImages(rsOuter.getString("filename"), rsInner.getString("filename"));
              }
            }
          }
        }
      }
      rsInner.close();
    }
    rsOuter.close();
  }

  public class Filewalker {

    public void walk(String path) {

      File root = new File(path);

      // Create a FilenameFilter
      FileFilter filter = new FileFilter() {
        public boolean accept(File file) // or accept(File f, String name)
        {
          // System.out.print(file.isDirectory()?"DIR: ":file.isFile()?"FILE: ":"NONE");
          // System.out.println(file + " <> " + file.getAbsoluteFile() + "{}" +
          // file.getAbsolutePath());

          return file.isDirectory()// process all directories and files ending with ".jpg"
              || (file.canRead() && file.getName().length() >= 3
                  && file.getName().substring(file.getName().length() - 3).equalsIgnoreCase("JPG"));
          // || (file.canRead()) && (file.getName().endsWith(".jpg") ||
          // file.getName().endsWith(".JPG"));
        }
      };
      // ! Create a FilenameFilter

      File[] list = root.listFiles(filter);

      if (list == null)
        return;

      for (File file : list) {
        if (file.isDirectory()) {
          walk(file.getAbsolutePath()); // recursive invocation to subdirectory
          // System.out.println( "Dir:" + file.getAbsoluteFile() );
        } else {
          // process this file - get its signature
          id++; // count file number used as primary key in DB
          System.out.format("\r%d >%s<                          ", id, file.getAbsoluteFile().toString());
          new CompressImage().run(file.getAbsoluteFile().toString());
        }
      }
    }
  }

  // compute similarity index (short signature as a highly blurred and compressed
  // image)
  class CompressImage {

    public void run(String filename) {

      // [Load image]
      Mat src = null;
      GripPipelineSimilar signatureImage = null;

      try {
        System.setErr(ps); // catch any OpenCV imread errors - redirect System.err in a string

        src = Imgcodecs.imread(filename, Imgcodecs.IMREAD_UNCHANGED);

        System.err.flush();

        System.setErr(realErr); // back to normal System.err

        // check for any errors from the imread err string
        // It can fail with a message to System.err, Mat output not being set or 0
        // length, or Exception thrown
        String OpenCVerr = baos.toString(); // get any OpenCV imread errors
        if (OpenCVerr.length() > 0) {
          System.err.println("\n\n\nError found image " + id + " >" + OpenCVerr + "<\n\n\n");
          baos.reset(); // clear error so we don't see it again
          return;
        }

        if (src == null || src.empty()) {
          System.err.format("\n\n\nCannot read image %d but no message or Exception thrown: %s\n\n\n", id, filename);
          return;
        }
        // ! [Load image]

        // [Compute Hash]
        signatureImage = new GripPipelineSimilar();

        long[] hash = { 0, 0, 0, 0 };

        boolean firstTimeWriteKohID = true;
        int startChannel = (BW ? 0 : COLOR ? 1 : 99); // if neither BW or COLOR don't do anything
        int endChannel = (COLOR ? 2 : BW ? 0 : -99);

        for (int channel = startChannel; channel <= endChannel; channel++) {

          signatureImage.process(src, channel);

          // System.out.println(similarity.cvAdaptivethresholdOutput());
          int size = (int) signatureImage.cvAdaptivethresholdOutput().total();

          if (kohRun && firstTimeWriteKohNumDimensions) {
            kohVectors.println(size * (endChannel - startChannel + 1));
            firstTimeWriteKohNumDimensions = false;
          }

          byte[] temp = new byte[size];

          signatureImage.cvAdaptivethresholdOutput().get(0, 0, temp); //

          // compress the 64 bits from each channel into 3 longs - hash[0] to hash[3] 3 is
          // still 0
          long mask = 1;

          for (int idx = 0; idx < Math.min(64, temp.length); idx++) {
            if (kohRun && firstTimeWriteKohID) {
              kohVectors.println("id" + id + " " + filename); // label for the Kohonen vector - use the file sequence number
              firstTimeWriteKohID = false;
            }
            if (temp[idx] != 0) {
              hash[channel] = hash[channel] | mask;
              if (kohRun) {
                kohVectors.println("1"); // Kohonen output 1 for this dimension
              }
            } else {
              if (kohRun) {
                kohVectors.println("0"); // Kohonen output 0 for this dimension
              }
            }
            mask = mask << 1;
          }
        } // end loop processing the 3 image channels Y U V

        statementInsert.execute("insert into Signature values(" + id + ", " + hash[0] + "," + hash[1]
          + "," + hash[2] + "," + hash[3] + "," + "'" + filename.replace("'", "''") + "')");

        // ! [Compute Hash]

      } catch (Exception e) {
        System.err.format("\n\n\nException thrown - Cannot process image %d:  %s  %s   \n\n\n", id,
          filename, e.toString());
        return;
      } finally {
        src.release();
        signatureImage.releaseMats(); // done with the Mats
      }
      // System.err.print(Runtime.getRuntime().freeMemory() + " ");
    }
  }

  private static void displayImages(String image1, String image2) {
    try {
      // execute command to display pair of similar images in external editor of
      // choice in the bat
      // imageEdit.bat "file1" "file2"
      List<String> command = new ArrayList<String>(); // build my command as a list of strings
      command.add("imageEdit.bat");
      command.add("\"" + image1 + "\"");
      command.add("\"" + image2 + "\"");

      ProcessBuilder pb1 = new ProcessBuilder(command);
      Process process1 = pb1.start();
      int errCode1 = process1.waitFor();
      command.clear();
      System.out.println("display images command executed, any errors? " + (errCode1 == 0 ? "No" : "Yes"));
      System.out.println("display images output:" + displayImagesOutput(process1.getInputStream()));
      System.out.println("display images errors:" + displayImagesOutput(process1.getErrorStream()));
    } catch (Exception ex2) {
      System.out.println("Error in display images process " + ex2);
    }
  }

  private static String displayImagesOutput(InputStream inputStream) throws IOException {
    StringBuilder sb = new StringBuilder();
    BufferedReader br = null;

    try {
      br = new BufferedReader(new InputStreamReader(inputStream));
      String line = null;
      while ((line = br.readLine()) != null) {
        sb.append(line + System.getProperty("line.separator"));
      }
    } finally {
      br.close();
    }

    return sb.toString();
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
