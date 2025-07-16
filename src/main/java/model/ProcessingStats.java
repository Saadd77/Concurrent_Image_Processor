package model;

/**
 * Processing statistics tracking
 */
public class ProcessingStats {
	public long totalTime;
	public int imagesProcessed;
	public int failedImages;

	@Override
	public String toString() {
		return String.format("Processed: %d images, Failed: %d, Time: %.2fs, Avg: %.2fs/image",
				imagesProcessed, failedImages, totalTime / 1000.0,
				imagesProcessed > 0 ? (totalTime / 1000.0) / imagesProcessed : 0);
	}
}