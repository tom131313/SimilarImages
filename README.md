# SimilarImages

Find jpg (or other types as desired with slight code modification) files that are similar to each other (by pairs).

This program is largely a conglomeration of code snippits from the Internet put together to suit my purposes to find duplicate (similar) images in my collection.

It uses H2 Java database and OpenCV Java.

The algorithm is mostly from the help of "findimagedupes" (2.19) with other ideas from StackOverflow comments.

Other comparison algorithms (SSIM, histogram and more) were tried but what's here is more than complicated enough to find my dupes.

GRIP was used to generate most of the image processing pipeline Java code.
