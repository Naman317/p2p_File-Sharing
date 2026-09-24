const getBackendUrl = () => {
  if (process.env.BACKEND_URL) {
    return process.env.BACKEND_URL;
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
