const nextConfig = {
  reactStrictMode: true,
  swcMinify: true,
  async rewrites() {
    return [
      {
        source: '/api/upload',
        destination: 'http://backend:8081/upload',
      },
      {
        source: '/api/download/:port',
        destination: 'http://backend:8081/download/:port',
      },
    ];
  },
};

module.exports = nextConfig;
