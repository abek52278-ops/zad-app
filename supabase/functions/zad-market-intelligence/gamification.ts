// Gamification Engine — Phase 3
// Achievements, badges, and leaderboard scoring system

interface Achievement {
  id: string;
  name: string;
  description: string;
  icon: string;
  points: number;
  condition: "contribution_count" | "price_accuracy" | "streak" | "total_score";
  threshold: number;
}

interface UserAchievement {
  user_id: string;
  achievement_id: string;
  unlocked_at: string;
  points_earned: number;
}

interface GamificationStats {
  total_contributions: number;
  current_streak: number;
  total_points: number;
  level: number;
  achievements_unlocked: number;
}

// Achievement definitions
export const ACHIEVEMENTS: Achievement[] = [
  {
    id: "first_step",
    name: "الخطوة الأولى",
    description: "أضف سعرك الأول",
    icon: "🌟",
    points: 10,
    condition: "contribution_count",
    threshold: 1,
  },
  {
    id: "rising_star",
    name: "نجم صاعد",
    description: "أضف 10 أسعار",
    icon: "⭐",
    points: 50,
    condition: "contribution_count",
    threshold: 10,
  },
  {
    id: "market_analyst",
    name: "محلل أسواق",
    description: "أضف 50 سعر",
    icon: "📊",
    points: 200,
    condition: "contribution_count",
    threshold: 50,
  },
  {
    id: "expert_reporter",
    name: "خبير التقارير",
    description: "أضف 100 سعر",
    icon: "🏆",
    points: 500,
    condition: "contribution_count",
    threshold: 100,
  },
  {
    id: "on_fire",
    name: "أسبوع متتالي",
    description: "ساهم 7 أيام متتالية",
    icon: "🔥",
    points: 100,
    condition: "streak",
    threshold: 7,
  },
  {
    id: "consistency",
    name: "الصبر والمثابرة",
    description: "مجموع 500 نقطة",
    icon: "💪",
    points: 150,
    condition: "total_score",
    threshold: 500,
  },
];

export function calculateLevel(totalPoints: number): number {
  // Level = (totalPoints / 100) + 1
  return Math.floor(totalPoints / 100) + 1;
}

export function calculateStreak(
  lastContributionDate: string | null,
  currentDate: Date = new Date()
): number {
  if (!lastContributionDate) return 0;

  const last = new Date(lastContributionDate);
  const current = new Date(currentDate);

  // Days between last contribution and today
  const diffTime = current.getTime() - last.getTime();
  const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));

  // If more than 1 day has passed, streak is broken
  if (diffDays > 1) return 0;

  // Otherwise, streak continues
  return 1;
}

export function getUnlockedAchievements(
  stats: GamificationStats
): Achievement[] {
  return ACHIEVEMENTS.filter((ach) => {
    switch (ach.condition) {
      case "contribution_count":
        return stats.total_contributions >= ach.threshold;
      case "streak":
        return stats.current_streak >= ach.threshold;
      case "total_score":
        return stats.total_points >= ach.threshold;
      default:
        return false;
    }
  });
}

export function getNextAchievement(
  stats: GamificationStats
): Achievement | null {
  const locked = ACHIEVEMENTS.filter((ach) => {
    switch (ach.condition) {
      case "contribution_count":
        return stats.total_contributions < ach.threshold;
      case "streak":
        return stats.current_streak < ach.threshold;
      case "total_score":
        return stats.total_points < ach.threshold;
      default:
        return false;
    }
  });

  if (locked.length === 0) return null;

  // Sort by points to get the next achievable one
  return locked.sort((a, b) => a.threshold - b.threshold)[0];
}

export function formatProgressToNextAchievement(
  stats: GamificationStats
): string {
  const next = getNextAchievement(stats);
  if (!next) return "لقد حققت جميع الإنجازات! 🎉";

  switch (next.condition) {
    case "contribution_count":
      return `${stats.total_contributions}/${next.threshold} مساهمة`;
    case "streak":
      return `${stats.current_streak}/${next.threshold} أيام متتالية`;
    case "total_score":
      return `${stats.total_points}/${next.threshold} نقطة`;
    default:
      return "قريب جداً!";
  }
}

export async function updateUserAchievements(
  sb: any,
  userId: string,
  stats: GamificationStats
): Promise<UserAchievement[]> {
  try {
    const unlockedAchievements = getUnlockedAchievements(stats);

    // Get already-unlocked achievements for this user
    const { data: existingAchievements } = await sb
      .from("user_achievements")
      .select("achievement_id")
      .eq("user_id", userId);

    const existingIds = (existingAchievements || []).map(
      (a: any) => a.achievement_id
    );

    // Find new achievements
    const newAchievements = unlockedAchievements.filter(
      (a) => !existingIds.includes(a.id)
    );

    if (newAchievements.length === 0) {
      return [];
    }

    // Insert new achievements
    const insertData = newAchievements.map((ach) => ({
      user_id: userId,
      achievement_id: ach.id,
      points_earned: ach.points,
      unlocked_at: new Date().toISOString(),
    }));

    const { data: inserted, error } = await sb
      .from("user_achievements")
      .insert(insertData)
      .select();

    if (error) {
      console.error("Error inserting achievements:", error);
      return [];
    }

    return inserted || [];
  } catch (err) {
    console.error("Error updating achievements:", err);
    return [];
  }
}

export async function getUserStats(
  sb: any,
  userId: string
): Promise<GamificationStats> {
  try {
    // Get contribution count
    const { data: contributions } = await sb
      .from("price_index")
      .select("id, timestamp")
      .eq("user_id", userId)
      .eq("source", "crowdsource")
      .order("timestamp", { ascending: false });

    const totalContributions = contributions?.length || 0;

    // Get total points from achievements
    const { data: achievements } = await sb
      .from("user_achievements")
      .select("points_earned");

    const totalPoints = (achievements || []).reduce(
      (sum: number, a: any) => sum + (a.points_earned || 0),
      0
    );

    // Calculate current streak
    const lastContribution = contributions?.[0]?.timestamp || null;
    const currentStreak = calculateStreak(lastContribution);

    const stats: GamificationStats = {
      total_contributions: totalContributions,
      current_streak: currentStreak,
      total_points: totalPoints,
      level: calculateLevel(totalPoints),
      achievements_unlocked: achievements?.length || 0,
    };

    return stats;
  } catch (err) {
    console.error("Error fetching user stats:", err);
    return {
      total_contributions: 0,
      current_streak: 0,
      total_points: 0,
      level: 1,
      achievements_unlocked: 0,
    };
  }
}
