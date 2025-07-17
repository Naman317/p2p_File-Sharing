/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  swcMinify: true,
  async rewrites() {
    return [
      {
        source: '/api/upload',
        destination: 'http://localhost:8081/upload',
      },
      {
        source: '/api/download/:port',
        destination: 'http://localhost:8081/download/:port',
      },
    ];
  },
}

module.exports = nextConfig
