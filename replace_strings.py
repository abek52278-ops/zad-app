import os

replacements = {
    '"مرحباً بعودتك"': 'stringResource(R.string.welcome_back)',
    '"تحميل..."': 'stringResource(R.string.loading)',
    '"مستخدم جديد"': 'stringResource(R.string.new_user)',
    '"المرتب المخصص"': 'stringResource(R.string.allocated_salary)',
    '"ريال"': 'stringResource(R.string.currency)',
    '"المصروف"': 'stringResource(R.string.expense)',
    '"إيداع"': 'stringResource(R.string.deposit)',
    '"المتبقي"': 'stringResource(R.string.remaining)',
    '"تحليل المصروفات"': 'stringResource(R.string.expense_analysis)',
    '"بقالة"': 'stringResource(R.string.grocery)',
    '"اشتراكات"': 'stringResource(R.string.subscriptions)',
    '"مطاعم"': 'stringResource(R.string.restaurants)',
    '"عناصر المخزون"': 'stringResource(R.string.inventory_items)',
    '"الاشتراكات الفعالة"': 'stringResource(R.string.active_subscriptions)',
    '"ترشيحات الأكلات من مخزونك"': 'stringResource(R.string.ai_meal_suggestions)',
    '"عرض الكل"': 'stringResource(R.string.view_all)',
    '"أحدث المصروفات"': 'stringResource(R.string.recent_transactions)',
    '"لا يوجد مصروفات حالياً"': 'stringResource(R.string.no_transactions)',
    '"عرض السجل الكامل"': 'stringResource(R.string.view_full_history)',
    '"تفعيل إشعارات البنك"': 'stringResource(R.string.enable_bank_notifications)',
    '"اسمح لزاد بقراءة إشعارات البنك لتسجيل مصاريفك تلقائياً."': 'stringResource(R.string.enable_bank_notifications_desc)',
    '"تفعيل"': 'stringResource(R.string.enable)',
    '"Loging"': 'stringResource(R.string.loging)',
    '"Login"': 'stringResource(R.string.login)',
    '"Singup"': 'stringResource(R.string.signup)',
    '"Enter your emails and password"': 'stringResource(R.string.enter_email_password)',
    '"Email"': 'stringResource(R.string.email)',
    '"Password"': 'stringResource(R.string.password)',
    '"Forgot Password?"': 'stringResource(R.string.forgot_password)',
    '"Don\'t have an account? "': 'stringResource(R.string.dont_have_account)',
    '"Close"': 'stringResource(R.string.close)'
}

def process_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        text = f.read()
    
    # replace strings
    for k, v in replacements.items():
        text = text.replace(k, v)
        
    # special replacements
    text = text.replace('"$totalSpent ريال"', 'totalSpent.toString() + " " + stringResource(R.string.currency)')
    text = text.replace('"$currentBudget ريال"', 'currentBudget.toString() + " " + stringResource(R.string.currency)')
    text = text.replace('"${tx.amount} ريال"', 'tx.amount.toString() + " " + stringResource(R.string.currency)')
    text = text.replace('"$percentage٪ من الميزانية"', 'stringResource(R.string.budget_percent, percentage)')
    text = text.replace('"$inventoryCount عنصر"', 'inventoryCount.toString() + " " + stringResource(R.string.inventory_items)')
    text = text.replace('"$subsCount اشتراكات"', 'subsCount.toString() + " " + stringResource(R.string.subscriptions)')
    
    # Add import if not exists
    if 'import androidx.compose.ui.res.stringResource' not in text:
        text = text.replace('import androidx.compose.runtime.Composable', 'import androidx.compose.ui.res.stringResource\nimport androidx.compose.runtime.Composable')
        
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(text)

process_file("e:/app zad/app/src/main/java/com/example/ui/screens/HomeScreen.kt")
process_file("e:/app zad/app/src/main/java/com/example/ui/screens/auth/LoginScreen.kt")
print("Done")
