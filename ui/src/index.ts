import {definePlugin} from '@halo-dev/ui-shared'
import IconHitokotoLogo from '~icons/my-icons/hitokoto-logo'
import {markRaw} from 'vue'
import './styles/tailwind.css'

export default definePlugin({
  components: {},
  routes: [
    {
      parentName: 'ToolsRoot',
      route: {
        path: '/hitokoto-hub',
        name: 'Hitokoto',
        component: () => import('./views/HomeView.vue'),
        meta: {
          title: '轻言管理',
          searchable: true,
          permissions: ['plugin:hitokoto-hub:view'],
          menu: {
            name: '轻言管理',
            icon: markRaw(IconHitokotoLogo),
            priority: 0,
          },
        },
      },
    },
  ],
  extensionPoints: {},
})
