package app;

import org.opencv.core.DMatch;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.SIFT;

import java.util.ArrayList;
import java.util.List;

class FeatureMatching {

 public List<DMatch> SIFTFLANNMatching(Mat img1, Mat img2) {

    //convert img1 & img2 to grey
    Utility.desaturate(img1, img1);
    Utility.desaturate(img2, img2);
    
    //-- Step 1: Detect the keypoints using SIFT Detector, compute the descriptors
    double contrastThreshold = 0.03; 
    double edgeThreshold = 2.0;
    double sigma = 1.0;
    int nOctaveLayers = 3; 
    int hessianThreshold = 400;

    SIFT detector = SIFT.create(hessianThreshold, nOctaveLayers, contrastThreshold, edgeThreshold, sigma);
    
    MatOfKeyPoint keypoints1 = new MatOfKeyPoint(), keypoints2 = new MatOfKeyPoint();
    Mat descriptors1 = new Mat(), descriptors2 = new Mat();
    
    detector.detectAndCompute(img1, new Mat(), keypoints1, descriptors1);
    detector.detectAndCompute(img2, new Mat(), keypoints2, descriptors2);
    
    //-- Step 2: Matching descriptor vectors with a FLANN based matcher
    // Since SIFT is a floating-point descriptor NORM_L2 is used
    DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.FLANNBASED);
    List<MatOfDMatch> knnMatches = new ArrayList<>();
    matcher.knnMatch(descriptors1, descriptors2, knnMatches, 2);
    
    //-- Filter matches using the Lowe's ratio test
    float ratioThresh = 0.7f;
    List<DMatch> listOfGoodMatches = new ArrayList<>();
    for (int i = 0; i < knnMatches.size(); i++) {
        if (knnMatches.get(i).rows() > 1) {
            DMatch[] matches = knnMatches.get(i).toArray();
            if (matches[0].distance < ratioThresh * matches[1].distance) {
                listOfGoodMatches.add(matches[0]);
            }
        }
    }
    return listOfGoodMatches;

    }
}
// import org.opencv.core.MatOfByte;
// import org.opencv.core.Scalar;
// import org.opencv.features2d.Features2d;
    // MatOfDMatch goodMatches = new MatOfDMatch();
    // goodMatches.fromList(listOfGoodMatches);
    // //-- Draw matches
    // Mat imgMatches = new Mat();
    // Features2d.drawMatches(img1, keypoints1, img2, keypoints2, goodMatches, imgMatches, Scalar.all(-1),
    //         Scalar.all(-1), new MatOfByte(), Features2d.DrawMatchesFlags_NOT_DRAW_SINGLE_POINTS);
    // //-- Show detected matches
    // HighGui.imshow("Good Matches", imgMatches);
    // HighGui.waitKey(0);
    // System.exit(0);