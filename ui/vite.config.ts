import { viteConfig } from '@halo-dev/ui-plugin-bundler-kit/vite'
import path from 'node:path'

import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import Icons from 'unplugin-icons/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { FileSystemIconLoader } from 'unplugin-icons/loaders'
import tailwindcss from '@tailwindcss/vite'

export default viteConfig({
  vite: {
    resolve: {
      alias: {
        '@': path.resolve(import.meta.dirname, 'src'),
      },
    },
    plugins: [
      Icons({
        compiler: 'vue3',
        customCollections: {
          'my-icons': FileSystemIconLoader('./src/assets/icons', (svg) => svg),
        },
      }),
      AutoImport({
        resolvers: [ElementPlusResolver()],
      }),
      Components({
        resolvers: [ElementPlusResolver()],
      }),
      tailwindcss(),
    ],
  },
})
