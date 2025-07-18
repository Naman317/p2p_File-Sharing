const isProd = process.env.NODE_ENV === 'production';

const nextConfig = {
  reactStrictMode: true,
  swcMinify: true,
  async rewrites() {
    return [
      {
        source: '/api/upload',
        destination: isProd
          ? 'https://p2p_file-sharing.railway.internal/upload'
          : 'http://localhost:8081/upload',
      },
      {
        source: '/api/download/:port',
        destination: isProd
          ? 'https://p2p_file-sharing.railway.internal/download/:port'
          : 'http://localhost:8081/download/:port',
      },
    ];
  },
};

module.exports = nextConfig;
