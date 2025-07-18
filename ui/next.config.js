const isProd = process.env.NODE_ENV === 'production';

const nextConfig = {
  reactStrictMode: true,
  swcMinify: true,
  async rewrites() {
    return [
      {
        source: '/api/upload',
        destination: isProd
          ? 'https://p2pfile.up.railway.app/upload'
          : 'http://localhost:8081/upload',
      },
      {
        source: '/api/download/:port',
        destination: isProd
          ? 'https://p2pfile.up.railway.app/download/:port'
          : 'http://localhost:8081/download/:port',
      },
    ];
  },
};

module.exports = nextConfig;
