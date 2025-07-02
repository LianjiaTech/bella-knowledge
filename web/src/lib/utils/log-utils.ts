import chalk from "chalk";

export class LogUtils {
  private static readonly isProd = process.env.NODE_ENV === "production";

  // 基础颜色方法
  static info(message: string) {
    if (this.isProd) return;
    console.log(chalk.gray("─".repeat(50)));
    console.log(chalk.blue(message));
    console.log(chalk.gray("─".repeat(50)));
  }

  static success(message: string) {
    if (this.isProd) return;
    console.log(chalk.gray("─".repeat(50)));
    console.log(chalk.green(message));
    console.log(chalk.gray("─".repeat(50)));
  }

  static warning(message: string) {
    if (this.isProd) return;
    console.log(chalk.gray("─".repeat(50)));
    console.log(chalk.yellow(message));
    console.log(chalk.gray("─".repeat(50)));
  }

  static error(message: string) {
    if (this.isProd) return;
    console.log(chalk.gray("─".repeat(50)));
    console.log(chalk.red(message));
    console.log(chalk.gray("─".repeat(50)));
  }
}
