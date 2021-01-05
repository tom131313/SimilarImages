# SimilarImages

Find jpg (or other types as desired with slight code modification) files that are similar to each other (by pairs).

This program is largely a conglomeration of code snippits from the Internet put together to suit my purposes to find duplicate (or similar) images in my collection.

It uses H2 Java database and OpenCV Java.

The algorithm is mostly from the help of "findimagedupes" (2.19) with other ideas from StackOverflow comments.

Since that relatively easily computed "signature" of an image (used to compare images) is prone to some failures - some black and white photos appear to be similar to images of  text - a more accurate MSSIM algorithm is used for comparing images that the signature deems similar.  MSSIM isn't used for all comparisions becasue it's a computer hog.  MSSIM  index is a little better than the hash code method but it is very expensive - a thousand times more cpu and elapsed time compared to the hash code.  It beats a hard drive to death - literally but it's still really slow on SSD.

Other comparison algorithms (SSIM, histogram and more) were tried but what's here is more than complicated enough to find highly accurately my dupes and similar images.

GRIP was used to generate most of the image processing pipeline Java code.

There are a few user settable parameters to direct the course of the comparisions.
Switch to output 0/1 valued vectors in the 192 dimensions - good for running through a Kohonen SOM program that's found on the Internet.
Switches to filter how similar images should be to be reported.
Switch to display the similar images including sending them to an external editting program.
Switch to specify which channels of YUV (image is converted from RGB) to compare.
