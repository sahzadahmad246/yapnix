import 'reflect-metadata';
import { NestFactory } from '@nestjs/core';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';
import { AppModule } from './app.module.js';
import { configureApp } from './configure-app.js';

async function bootstrap(): Promise<void> {
  const app = await NestFactory.create(AppModule);
  configureApp(app);

  const corsOrigins = process.env.CORS_ORIGINS?.split(',').map(origin => origin.trim()).filter(Boolean);
  if (process.env.NODE_ENV === 'production' && !corsOrigins?.length) {
    throw new Error('CORS_ORIGINS must list the Studio origin in production');
  }
  app.enableCors({ origin: corsOrigins?.length ? corsOrigins : ['http://localhost:3000'] });

  const swaggerConfig = new DocumentBuilder()
    .setTitle('Yapnix API')
    .setDescription('Public gameplay and protected administration APIs')
    .setVersion('0.1.0')
    .addBearerAuth()
    .build();

  SwaggerModule.setup('api/docs', app, SwaggerModule.createDocument(app, swaggerConfig));
  await app.listen(process.env.PORT ?? 3333);
}

void bootstrap();
