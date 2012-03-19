/*
 * Hale is highly moddable tactical RPG.
 * Copyright (C) 2011 Jared Stephen
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package net.sf.hale.mainmenu;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import net.sf.hale.util.FileUtil;
import net.sf.hale.util.Logger;

/**
 * A thread that is run to download and extract an update from the server
 * @author Jared
 *
 */

public class UpdateTask extends Thread {
	private final CheckForUpdatesTask.UpdateInfo updateInfo;
	
	private long totalBytesToExtract;
	private long bytesExtracted;
	
	private int bytesDownloaded;
	
	private volatile String error;
	private volatile boolean canceled;
	
	private volatile String taskText;
	
	private volatile float progress;
	
	private volatile boolean done;
	
	/**
	 * Creates a new UpdateTask which will download and apply the update
	 * from the specified file
	 * @param updateInfo the information about the file to be downloaded
	 */
	
	public UpdateTask(CheckForUpdatesTask.UpdateInfo updateInfo) {
		this.updateInfo = updateInfo;
		
		taskText = "Checking for update";
		progress = 0.0f;
	}
	
	/**
	 * Returns a string description of any error this task has encountered, or null if
	 * no error has occurred
	 * @return a description of any error that has occurred
	 */
	
	public String getError() {
		return error;
	}
	
	/**
	 * Returns true if and only if this update task has completed
	 * @return true if this update task has completed successfully
	 */
	
	public boolean isDone() {
		return done;
	}
	
	/**
	 * Causes this task to immediately cancel anything it is currently doing
	 */
	
	public void cancel() {
		canceled = true;
	}
	
	/**
	 * Gets the current amount of progress this update task has made, as a fraction between 0.0 and 1.0
	 * @return the amount of progress
	 */
	
	public float getProgress() {
		return progress;
	}
	
	public String getTaskText() {
		return taskText;
	}
	
	private void updateProgressDownload() {
		// the download is considered to be the first half of the update process
		taskText = "Downloading (" + (bytesDownloaded / 1024) + " KB / " + (updateInfo.fileSize / 1024) + " KB)";
		progress = 0.5f * ((float)bytesDownloaded) / ((float)updateInfo.fileSize);
	}
	
	private void downloadUpdateFile(File updateFile) {
		BufferedInputStream in = null;
		FileOutputStream fout = null;
		try {
			in = new BufferedInputStream(new URL(updateInfo.URL).openStream());
			fout = new FileOutputStream(updateFile);
			
			byte[] buffer = new byte[2048];
			int count;
			while ( (count = in.read(buffer, 0, 2048)) != -1) {
				if (canceled) break;
				
				// write the bytes to a file
				fout.write(buffer, 0, count);
				
				// update the progress description
				bytesDownloaded += count;
				
				updateProgressDownload();
			}
			
		} catch (Exception e) {
			Logger.appendToErrorLog("Error downloading update", e);
			error = "Error retrieving file from server";
		}
		
		try {
			if (in != null)
				in.close();

			if (fout != null)
				fout.close();
			
		} catch (Exception e) {
			Logger.appendToErrorLog("Error closing stream while updating", e);
		}
	}
	
	private long computeExtractedSize(File zipFile) throws IOException {
		long totalSize = 0l;
		
		ZipInputStream in = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)));
		
		ZipEntry entry;
		while ( (entry = in.getNextEntry()) != null ) {
			long size = entry.getSize();
			
			if (size != -1) {
				totalSize += size;
			}
		}
		
		return totalSize;
	}
	
	private void extractUpdateZipFile(File zipFile) throws IOException {
		int bufferSize = 2048;
		byte[] buffer = new byte[bufferSize];
		
		ZipInputStream in = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)));
		
		ZipEntry entry;
		while ( (entry = in.getNextEntry()) != null ) {
			
			updateProgressExtract();
			
			if (canceled)
				break;
			
			// don't extract the top level directory
			if (entry.getName().equals("hale/")) continue;
			
			String outputPath = FileUtil.getRelativePath("hale", entry.getName());
			File entryOut = new File(outputPath);
			
			System.out.println(outputPath);
			
			if (entry.isDirectory()) {
				entryOut.mkdirs();
			} else {
				int count = 0;

				BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(entryOut), bufferSize);
				
				while ( (count = in.read(buffer, 0, bufferSize)) > 0 ) {
					out.write(buffer, 0, count);
				}
				out.flush();
				out.close();
				
				bytesExtracted += count;
			}
		}
		
		in.close();
	}
	
	private void updateProgressExtract() {
		// the download is considered to be the first half of the update process
		taskText = "Extracting (" + (bytesExtracted / 1024) + " KB / " + (totalBytesToExtract / 1024) + " KB)";
		progress = 0.5f + 0.5f * ((float)bytesExtracted) / ((float)totalBytesToExtract);
	}
	
	@Override public void run() {
		// create the updates directory if it does not already exist
		File updatesDir = new File("updates");
		if (!updatesDir.isDirectory())
			updatesDir.mkdir();
		
		File updateFile = new File("updates" + File.separatorChar + updateInfo.targetName);
		if (updateFile.isFile()) {
			// if the file exists, then check to see if the hashcode matches the server update
			String localMD5 = FileUtil.getMD5Sum(updateFile);
			
			if (localMD5.equals(updateInfo.md5Sum.toUpperCase())) {
				// if the hashcodes match, we already have the file, so no need to download
			} else {
				// the update does not match, so it is probably corrupted.  delete it and try again
				updateFile.delete();
				downloadUpdateFile(updateFile);
			}
		} else {
			// the file does not exist, so download it
			downloadUpdateFile(updateFile);
		}
		
		// check for cancelation
		if (canceled || error != null)
			return;
		
		// we should now have the update file downloaded
		taskText = "Applying Update";
		progress = 0.5f;
		
		// figure out how many bytes we need to extract
		try {
			totalBytesToExtract = computeExtractedSize(updateFile);
		} catch (IOException e) {
			Logger.appendToErrorLog("Error computing extraction size", e);
			error = "Error applying update!";
		}
		bytesExtracted = 0l;
		
		updateProgressExtract();
		
		// check for cancelation
		if (canceled || error != null)
			return;
		
		// now apply the update
		try {
			extractUpdateZipFile(updateFile);
		} catch (IOException e) {
			Logger.appendToErrorLog("Error applying update", e);
			error = "Error applying update!";
		}
		
		// check for cancelation
		if (canceled || error != null)
			return;

		done = true;
	}
}
