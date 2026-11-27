import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../state/youtube_controller.dart';
import '../theme/app_colors.dart';
import '../theme/app_text.dart';
import '../widgets/primary_button.dart';

/// Settings tab: connect video platforms. YouTube is the first.
class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final top = MediaQuery.paddingOf(context).top;
    return ListView(
      padding: EdgeInsets.fromLTRB(16, top + 16, 16, 24),
      children: [
        Text('Settings', style: AppText.outfit(34, weight: 500, color: Colors.black)),
        const SizedBox(height: 22),
        const _YouTubeCard(),
      ],
    );
  }
}

class _YouTubeCard extends StatelessWidget {
  const _YouTubeCard();

  @override
  Widget build(BuildContext context) {
    final youtube = context.watch<YouTubeController>();
    final account = youtube.account;
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: AppColors.surfaceMuted,
        borderRadius: BorderRadius.circular(26),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 44,
                height: 44,
                decoration: BoxDecoration(
                  color: const Color(0xFFFF0033),
                  borderRadius: BorderRadius.circular(14),
                ),
                child: const Icon(Icons.play_arrow_rounded, color: Colors.white, size: 30),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('YouTube', style: AppText.urbanist(20, weight: 700)),
                    Text(
                      account == null ? 'Not connected' : 'Connected',
                      style: AppText.urbanist(
                        13,
                        weight: 600,
                        color: account == null ? AppColors.sectionLabel : AppColors.primary,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),
          Text(
            account == null
                ? 'Connect your account to upload your stories to YouTube straight from the app.'
                : 'Uploading as ${account.email}.',
            style: AppText.urbanist(15, weight: 500, color: AppColors.body),
          ),
          if (youtube.error != null) ...[
            const SizedBox(height: 10),
            Text(
              youtube.error!,
              style: AppText.urbanist(14, weight: 600, color: AppColors.danger),
            ),
          ],
          if (account == null && !youtube.isConfigured) ...[
            const SizedBox(height: 10),
            Text(
              'This build has no Google client id yet, so sign-in is unavailable.',
              style: AppText.urbanist(13, weight: 500, color: AppColors.sectionLabel),
            ),
          ],
          const SizedBox(height: 18),
          if (account == null)
            PrimaryButton(
              label: youtube.isBusy ? 'Connecting..' : 'Connect with YouTube',
              color: const Color(0xFFFF0033),
              onPressed: youtube.isBusy ? null : youtube.connect,
            )
          else
            OutlinedButton(
              style: OutlinedButton.styleFrom(
                minimumSize: const Size.fromHeight(52),
                side: const BorderSide(color: AppColors.primary),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),
              ),
              onPressed: youtube.isBusy ? null : youtube.disconnect,
              child: Text(
                'Disconnect',
                style: AppText.urbanist(16, weight: 700, color: AppColors.primary),
              ),
            ),
        ],
      ),
    );
  }
}
