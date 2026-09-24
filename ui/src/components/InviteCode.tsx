'use client';

import { useState, useEffect } from 'react';
import { FiCopy, FiCheck, FiClock } from 'react-icons/fi';

interface InviteCodeProps {
  port: number | null;
  ttlMinutes?: number;
}

export default function InviteCode({ port, ttlMinutes = 30 }: InviteCodeProps) {
  const [copied, setCopied] = useState(false);
  const [secondsRemaining, setSecondsRemaining] = useState(ttlMinutes * 60);

  useEffect(() => {
    if (!port) return;
    setSecondsRemaining(ttlMinutes * 60);

    const interval = setInterval(() => {
      setSecondsRemaining((prev) => {
        if (prev <= 1) {
          clearInterval(interval);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(interval);
  }, [port, ttlMinutes]);

  if (!port) return null;

  const minutes = Math.floor(secondsRemaining / 60);
  const seconds = secondsRemaining % 60;
  const isExpired = secondsRemaining === 0;

  const copyToClipboard = async () => {
    try {
      if (navigator.clipboard && navigator.clipboard.writeText) {
        await navigator.clipboard.writeText(port.toString());
      } else {
        const textArea = document.createElement('textarea');
        textArea.value = port.toString();
        document.body.appendChild(textArea);
        textArea.select();
        document.execCommand('copy');
        document.body.removeChild(textArea);
      }
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch (err) {
      console.error('Failed to copy to clipboard', err);
    }
  };

  return (
    <div className={`mt-6 p-5 border rounded-xl transition-all ${
      isExpired ? 'bg-red-50 border-red-200' : 'bg-green-50 border-green-200'
    }`}>
      <div className="flex items-center justify-between mb-2">
        <h3 className={`text-lg font-semibold ${isExpired ? 'text-red-800' : 'text-green-800'}`}>
          {isExpired ? 'Sharing Code Expired' : 'File Ready to Share!'}
        </h3>
        <div className={`flex items-center space-x-1.5 text-xs font-medium px-2.5 py-1 rounded-full ${
          isExpired ? 'bg-red-100 text-red-700' : 'bg-green-100 text-green-700'
        }`}>
          {!isExpired && (
            <span className="w-2 h-2 rounded-full bg-green-500 animate-pulse"></span>
          )}
          <FiClock className="w-3.5 h-3.5" />
          <span>
            {isExpired ? 'Expired' : `${minutes}m ${seconds < 10 ? '0' : ''}${seconds}s remaining`}
          </span>
        </div>
      </div>

      <p className={`text-sm mb-4 ${isExpired ? 'text-red-600' : 'text-green-600'}`}>
        {isExpired
          ? 'This invite code has expired. Please re-upload the file to generate a new code.'
          : 'Share this invite code with the recipient to let them download the file directly:'}
      </p>

      {!isExpired && (
        <div className="flex items-center shadow-sm">
          <div className="flex-1 bg-white p-3.5 rounded-l-lg border border-r-0 border-gray-300 font-mono text-xl tracking-wider text-gray-800 font-bold">
            {port}
          </div>
          <button
            onClick={copyToClipboard}
            className="p-3.5 bg-blue-600 hover:bg-blue-700 active:bg-blue-800 text-white rounded-r-lg transition-colors flex items-center justify-center min-w-[54px]"
            aria-label="Copy invite code"
          >
            {copied ? <FiCheck className="w-5 h-5 text-white" /> : <FiCopy className="w-5 h-5 text-white" />}
          </button>
        </div>
      )}

      <p className="mt-3 text-xs text-gray-500">
        {isExpired
          ? 'Expired temporary files are automatically cleaned up to save server resources.'
          : 'Transfers are streamed directly between peers and expire after 30 minutes.'}
      </p>
    </div>
  );
}
