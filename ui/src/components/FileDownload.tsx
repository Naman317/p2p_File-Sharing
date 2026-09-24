'use client';

import { useState } from 'react';
import { FiDownload } from 'react-icons/fi';

interface FileDownloadProps {
  onDownload: (port: number) => Promise<void>;
  isDownloading: boolean;
  downloadProgress?: number;
}

export default function FileDownload({ onDownload, isDownloading, downloadProgress = 0 }: FileDownloadProps) {
  const [inviteCode, setInviteCode] = useState('');
  const [error, setError] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    const port = parseInt(inviteCode.trim(), 10);
    if (isNaN(port) || port <= 0 || port > 65535) {
      setError('Please enter a valid port number (1-65535)');
      return;
    }

    try {
      await onDownload(port);
    } catch {
      setError('Failed to download the file. Please check the invite code and try again.');
    }
  };

  return (
    <div className="space-y-4">
      <div className="bg-blue-50 p-4 rounded-lg border border-blue-100">
        <h3 className="text-lg font-medium text-blue-800 mb-2">Receive a File</h3>
        <p className="text-sm text-blue-600 mb-0">
          Enter the invite code shared with you to download the file.
        </p>
      </div>

      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label htmlFor="inviteCode" className="block text-sm font-medium text-gray-700 mb-1">
            Invite Code
          </label>
          <input
            type="text"
            id="inviteCode"
            value={inviteCode}
            onChange={(e) => setInviteCode(e.target.value)}
            placeholder="Enter the invite code (port number)"
            className="input-field"
            disabled={isDownloading}
            required
          />
          {error && <p className="mt-1 text-sm text-red-600">{error}</p>}
        </div>

        {isDownloading && (
          <div className="p-4 bg-blue-50 border border-blue-200 rounded-lg space-y-2">
            <div className="flex justify-between text-sm font-medium text-blue-700">
              <span>Downloading file...</span>
              <span>{downloadProgress > 0 ? `${downloadProgress}%` : 'Streaming...'}</span>
            </div>
            <div className="w-full bg-blue-200 rounded-full h-2.5 overflow-hidden">
              <div
                className="bg-blue-600 h-2.5 rounded-full transition-all duration-300 ease-out"
                style={{ width: `${downloadProgress > 0 ? downloadProgress : 100}%` }}
              ></div>
            </div>
          </div>
        )}

        <button
          type="submit"
          className="btn-primary flex items-center justify-center w-full"
          disabled={isDownloading}
        >
          {isDownloading ? (
            <span>Downloading...</span>
          ) : (
            <>
              <FiDownload className="mr-2" />
              <span>Download File</span>
            </>
          )}
        </button>
      </form>
    </div>
  );
}
