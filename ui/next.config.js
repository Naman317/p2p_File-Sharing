const getBackendUrl = () => {
  if (process.env.BACKEND_URL) {
    let url = process.env.BACKEND_URL.trim();
    if (!url.startsWith('http://') && !url.startsWith('https://')) {
      url = `http://${url}`;
    }
    return url;
  }
  if (process.env.DOCKER_ENV === 'true') {
    return 'http://backend:8081';
  }
  return 'http://localhost:8081';
};

const backendUrl = getBackendUrl();

const nextConfig = {
  reactStrictMode: true,
  swcMinify: true,
  async rewrites() {
    return [
      {
        source: '/api/upload',
        destination: `${backendUrl}/upload`,
      },
      {
        source: '/api/download/:port',
        destination: `${backendUrl}/download/:port`,
      },
    ];
  },
};

module.exports = nextConfig;
