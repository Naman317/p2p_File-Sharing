'use client';

import { useState } from 'react';
import FileUpload from '@/components/FileUpload';
import FileDownload from '@/components/FileDownload';
import InviteCode from '@/components/InviteCode';
import axios from 'axios';

const apiBase = (process.env.NEXT_PUBLIC_API_URL || '').replace(/\/$/, '');
const uploadUrl = apiBase ? `${apiBase}/upload` : '/api/upload';
const getDownloadUrl = (port: number) => (apiBase ? `${apiBase}/download/${port}` : `/api/download/${port}`);

export default function Home() {
  const [uploadedFile, setUploadedFile] = useState<File | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [isDownloading, setIsDownloading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [downloadProgress, setDownloadProgress] = useState(0);
  const [port, setPort] = useState<number | null>(null);
  const [activeTab, setActiveTab] = useState<'upload' | 'download'>('upload');

  const handleFileUpload = async (file: File) => {
    setUploadedFile(file);
    setIsUploading(true);
    setUploadProgress(0);

    try {
      const formData = new FormData();
      formData.append('file', file);

      const response = await axios.post(uploadUrl, formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
        onUploadProgress: (progressEvent) => {
          if (progressEvent.total) {
            const percent = Math.round((progressEvent.loaded * 100) / progressEvent.total);
            setUploadProgress(percent);
          } else {
            setUploadProgress((prev) => Math.min(prev + 10, 90));
          }
        },
      });

      setUploadProgress(100);
      setPort(response.data.port);
    } catch (error: unknown) {
      console.error('Error uploading file:', error);
      let errorMsg = 'Network error or backend starting up';
      if (axios.isAxiosError(error)) {
        errorMsg = error.response?.data?.error || error.message || errorMsg;
      }
      alert(`Upload failed: ${errorMsg}. If the backend just woke up on Render, please wait 30 seconds and try again.`);
    } finally {
      setIsUploading(false);
    }
  };

  const handleDownload = async (port: number) => {
    setIsDownloading(true);
    setDownloadProgress(0);

    try {
      const response = await axios.get(getDownloadUrl(port), {
        responseType: 'blob',
        onDownloadProgress: (progressEvent) => {
          if (progressEvent.total) {
            const percent = Math.round((progressEvent.loaded * 100) / progressEvent.total);
            setDownloadProgress(percent);
          }
        },
      });

      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;

      // Extract filename from Content-Disposition header
      const headers = response.headers;
      let contentDisposition = '';

      for (const key in headers) {
        if (key.toLowerCase() === 'content-disposition') {
          contentDisposition = headers[key];
          break;
        }
      }

      let filename = 'downloaded-file';
      if (contentDisposition) {
        const filenameMatch = contentDisposition.match(/filename="(.+)"/);
        if (filenameMatch && filenameMatch.length === 2) {
          filename = filenameMatch[1];
        }
      }

      link.setAttribute('download', filename);
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    } catch (error: unknown) {
      console.error('Error downloading file:', error);
      let errorMsg = 'Failed to download file';
      if (axios.isAxiosError(error)) {
        errorMsg = error.response?.data?.error || error.message || errorMsg;
      }
      alert(`Download failed: ${errorMsg}. Please check the invite code and try again.`);
    } finally {
      setIsDownloading(false);
    }
  };

  return (
    <div className="container mx-auto px-4 py-10 max-w-4xl">
      <header className="text-center mb-10">
        <h1 className="text-4xl font-extrabold text-blue-600 tracking-tight mb-2">PeerLink</h1>
        <p className="text-lg text-gray-600">High-Performance P2P File Sharing with Direct Socket Transfer</p>
      </header>

      <div className="bg-white rounded-2xl shadow-xl border border-gray-100 p-8">
        <div className="flex border-b border-gray-200 mb-6">
          <button
            className={`pb-3 px-5 font-semibold text-base transition-all ${
              activeTab === 'upload'
                ? 'text-blue-600 border-b-2 border-blue-600'
                : 'text-gray-500 hover:text-gray-800'
            }`}
            onClick={() => setActiveTab('upload')}
          >
            Share a File
          </button>
          <button
            className={`pb-3 px-5 font-semibold text-base transition-all ${
              activeTab === 'download'
                ? 'text-blue-600 border-b-2 border-blue-600'
                : 'text-gray-500 hover:text-gray-800'
            }`}
            onClick={() => setActiveTab('download')}
          >
            Receive a File
          </button>
        </div>

        {activeTab === 'upload' ? (
          <div>
            <FileUpload
              onFileUpload={handleFileUpload}
              isUploading={isUploading}
              uploadProgress={uploadProgress}
            />

            {uploadedFile && !isUploading && (
              <div className="mt-4 p-3 bg-gray-50 border border-gray-200 rounded-lg flex items-center justify-between">
                <p className="text-sm text-gray-700">
                  Selected file: <span className="font-semibold">{uploadedFile.name}</span> ({(uploadedFile.size / 1024).toFixed(1)} KB)
                </p>
                <span className="text-xs font-medium text-green-700 bg-green-100 px-2 py-0.5 rounded-full">
                  Uploaded
                </span>
              </div>
            )}

            <InviteCode port={port} ttlMinutes={30} />
          </div>
        ) : (
          <div>
            <FileDownload
              onDownload={handleDownload}
              isDownloading={isDownloading}
              downloadProgress={downloadProgress}
            />
          </div>
        )}
      </div>

      <footer className="mt-12 text-center text-gray-400 text-sm">
        <p>PeerLink &copy; {new Date().getFullYear()} &bull; Direct Socket Stream &bull; 30-min Auto-Expiration</p>
      </footer>
    </div>
  );
}
